package com.testdatagen.engine;

import com.testdatagen.engine.impl.DefaultGenerator;
import com.testdatagen.engine.impl.ForeignKeyGenerator;
import com.testdatagen.engine.impl.LlmBatchGenerator;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.DataGenRequest;
import com.testdatagen.model.dto.DataPreviewResponse;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DataGenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(DataGenerationPipeline.class);

    private final ConnectionService connectionService;
    private final RuleMatchingEngine ruleMatchingEngine;
    private final LlmService llmService;
    private final DataWriterService dataWriterService;
    private final int llmBatchSize;
    private final int insertBatchSize;
    private final ExecutorService llmExecutor;

    /**
     * 生成 generatedPkMap 的 key，格式为 "tableName.columnName" (全小写)，
     * 确保大小写不敏感匹配，并支持复合主键场景（不同列各自独立存储）。
     */
    private static String pkMapKey(String tableName, String columnName) {
        return tableName.toLowerCase() + "." + columnName.toLowerCase();
    }

    public DataGenerationPipeline(ConnectionService connectionService,
                                   RuleMatchingEngine ruleMatchingEngine,
                                   LlmService llmService,
                                   DataWriterService dataWriterService,
                                   int llmBatchSize, int insertBatchSize,
                                   @Autowired @Qualifier("dataGenExecutor") Executor dataGenExecutor) {
        this.connectionService = connectionService;
        this.ruleMatchingEngine = ruleMatchingEngine;
        this.llmService = llmService;
        this.dataWriterService = dataWriterService;
        this.llmBatchSize = llmBatchSize;
        this.insertBatchSize = insertBatchSize;
        // 创建专用于大模型调用的线程池
        this.llmExecutor = Executors.newFixedThreadPool(
                Math.max(Runtime.getRuntime().availableProcessors() * 2, 8),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("llm-call-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Generate preview data (small sample, no writing to DB).
     * 同层级的表并行生成数据，不同层级按依赖顺序串行执行。
     */
    public DataPreviewResponse preview(DataGenRequest request, SqlAnalysisResult analysisResult) {
        DataPreviewResponse response = new DataPreviewResponse();
        Map<String, List<Map<String, Object>>> tableData = new ConcurrentHashMap<>();
        int previewRows = request.getRowCount();

        Map<String, List<Object>> generatedPkMap = new ConcurrentHashMap<>();

        // 按依赖层级分组，同层级的表可以并行生成
        List<List<String>> levels = buildDependencyLevels(analysisResult);

        for (List<String> level : levels) {
            if (level.size() == 1) {
                // 单表直接串行处理
                String tableName = level.get(0);
                generateTablePreviewData(tableName, request, analysisResult, generatedPkMap, tableData, previewRows);
            } else {
                // 多表并行处理
                List<CompletableFuture<Void>> futures = level.stream()
                        .map(tableName -> CompletableFuture.runAsync(() ->
                                generateTablePreviewData(tableName, request, analysisResult, generatedPkMap, tableData, previewRows),
                                llmExecutor))
                        .collect(Collectors.toList());

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    log.error("并行生成预览数据异常: {}", e.getMessage(), e);
                    throw new RuntimeException("生成预览数据失败: " + e.getMessage(), e);
                }
            }
        }

        // 按 generationOrder 排序输出
        Map<String, List<Map<String, Object>>> orderedData = new LinkedHashMap<>();
        for (String tableName : analysisResult.getGenerationOrder()) {
            if (tableData.containsKey(tableName)) {
                orderedData.put(tableName, tableData.get(tableName));
            }
        }

        response.setTableData(orderedData);
        response.setGenerationOrder(analysisResult.getGenerationOrder());
        return response;
    }

    /**
     * 生成单个表的预览数据（可被并行调用）
     */
    private void generateTablePreviewData(String tableName, DataGenRequest request,
                                           SqlAnalysisResult analysisResult,
                                           Map<String, List<Object>> generatedPkMap,
                                           Map<String, List<Map<String, Object>>> tableData,
                                           int previewRows) {
        TableMetadata tableMeta = analysisResult.getTableMetadataMap().get(tableName);
        if (tableMeta == null) return;

        Map<String, FieldGenerator> generators = ruleMatchingEngine.matchRules(
                tableName, tableMeta.getColumns(), request.getFieldRules(), request.getRuleSetId());

        setupForeignKeyGenerators(tableMeta, generators, generatedPkMap, request.getConnectionId());
        preFillLlmGenerators(tableName, tableMeta, generators, previewRows, request.getModels());

        List<Map<String, Object>> rows = generateRows(tableMeta, generators, previewRows);
        tableData.put(tableName, rows);

        trackGeneratedPks(tableMeta, rows, generatedPkMap);
    }

    /**
     * Generate data and write to DB in a single transaction.
     * 同层级的表并行生成数据，写入时在同一事务下顺序执行。
     * All tables are written within the same transaction - if any table fails, all writes are rolled back.
     * Returns map of tableName -> rows written count.
     */
    public Map<String, Integer> execute(DataGenRequest request, SqlAnalysisResult analysisResult) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, List<Object>> generatedPkMap = new ConcurrentHashMap<>();

        // 按依赖层级并行生成所有表的数据
        Map<String, TableWriteData> allTableDataMap = new ConcurrentHashMap<>();

        List<List<String>> levels = buildDependencyLevels(analysisResult);

        for (List<String> level : levels) {
            if (level.size() == 1) {
                String tableName = level.get(0);
                generateTableExecuteData(tableName, request, analysisResult, generatedPkMap, allTableDataMap);
            } else {
                List<CompletableFuture<Void>> futures = level.stream()
                        .map(tableName -> CompletableFuture.runAsync(() ->
                                generateTableExecuteData(tableName, request, analysisResult, generatedPkMap, allTableDataMap),
                                llmExecutor))
                        .collect(Collectors.toList());

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    log.error("并行生成数据异常: {}", e.getMessage(), e);
                    throw new RuntimeException("生成数据失败: " + e.getMessage(), e);
                }
            }
        }

        // 按 generationOrder 排列写入顺序
        List<TableWriteData> allTableData = new ArrayList<>();
        for (String tableName : analysisResult.getGenerationOrder()) {
            TableWriteData data = allTableDataMap.get(tableName);
            if (data != null) {
                allTableData.add(data);
                result.put(tableName, data.rows.size());
                log.info("Table {} generated {} rows", tableName, data.rows.size());
            }
        }

        // 在同一事务下写入所有表
        try (java.sql.Connection conn = connectionService.getConnection(request.getConnectionId())) {
            conn.setAutoCommit(false);
            try {
                for (TableWriteData tableData : allTableData) {
                    if (tableData.rows.isEmpty() || tableData.columns.isEmpty()) continue;
                    List<Object> generatedKeys = dataWriterService.writeRowsOnConnection(
                            conn, tableData.tableName, tableData.columns, tableData.rows, insertBatchSize);
                    if (!generatedKeys.isEmpty()) {
                        generatedPkMap.put(tableData.tableName, generatedKeys);
                    }
                }
                conn.commit();
                log.info("事务提交成功，所有表数据写入完成");
            } catch (Exception e) {
                conn.rollback();
                log.error("事务回滚，写入失败: {}", e.getMessage());
                throw new RuntimeException("写入数据失败，已回滚所有操作: " + e.getMessage(), e);
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("获取数据库连接失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 生成单个表的执行数据（可被并行调用）
     */
    private void generateTableExecuteData(String tableName, DataGenRequest request,
                                           SqlAnalysisResult analysisResult,
                                           Map<String, List<Object>> generatedPkMap,
                                           Map<String, TableWriteData> allTableDataMap) {
        TableMetadata tableMeta = analysisResult.getTableMetadataMap().get(tableName);
        if (tableMeta == null) return;

        Map<String, FieldGenerator> generators = ruleMatchingEngine.matchRules(
                tableName, tableMeta.getColumns(), request.getFieldRules(), request.getRuleSetId());

        setupForeignKeyGenerators(tableMeta, generators, generatedPkMap, request.getConnectionId());
        preFillLlmGenerators(tableName, tableMeta, generators, request.getRowCount(), request.getModels());

        List<Map<String, Object>> rows = generateRows(tableMeta, generators, request.getRowCount());

        // Build column list (exclude auto_increment)
        List<String> columns = new ArrayList<>();
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (!col.isAutoIncrement() && generators.containsKey(col.getColumnName())) {
                columns.add(col.getColumnName());
            }
        }

        // Track PKs (非自增主键直接从生成数据中获取)
        trackGeneratedPks(tableMeta, rows, generatedPkMap);

        allTableDataMap.put(tableName, new TableWriteData(tableName, columns, rows, tableMeta));
    }

    private static class TableWriteData {
        final String tableName;
        final List<String> columns;
        final List<Map<String, Object>> rows;
        final TableMetadata tableMeta;

        TableWriteData(String tableName, List<String> columns, List<Map<String, Object>> rows, TableMetadata tableMeta) {
            this.tableName = tableName;
            this.columns = columns;
            this.rows = rows;
            this.tableMeta = tableMeta;
        }
    }

    private void setupForeignKeyGenerators(TableMetadata tableMeta, Map<String, FieldGenerator> generators,
                                            Map<String, List<Object>> generatedPkMap, Long connectionId) {
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (col.getReferencedTable() != null && generators.get(col.getColumnName()) instanceof ForeignKeyGenerator) {
                ForeignKeyGenerator fkGen = (ForeignKeyGenerator) generators.get(col.getColumnName());
                // 使用规范化 key 查找父表PK值（大小写不敏感 + 精确到列）
                String key = pkMapKey(col.getReferencedTable(), col.getReferencedColumn());
                List<Object> parentKeys = generatedPkMap.get(key);
                if (parentKeys != null && !parentKeys.isEmpty()) {
                    log.info("表 {}.{} FK注入: 从本次生成的父表 {}.{} 获取到 {} 个parentKeys",
                            tableMeta.getTableName(), col.getColumnName(),
                            col.getReferencedTable(), col.getReferencedColumn(), parentKeys.size());
                    fkGen.setParentKeys(parentKeys);
                } else {
                    // 本次未生成父表数据，回退到数据库查询已有PK
                    log.info("表 {}.{} FK回退: generatedPkMap中未找到key={}, 将从数据库查询已有数据",
                            tableMeta.getTableName(), col.getColumnName(), key);
                    List<Object> existingKeys = fetchExistingKeys(connectionId, col.getReferencedTable(), col.getReferencedColumn());
                    if (!existingKeys.isEmpty()) {
                        log.info("表 {}.{} FK回退: 从数据库获取到 {} 个已有key",
                                tableMeta.getTableName(), col.getColumnName(), existingKeys.size());
                        fkGen.setParentKeys(existingKeys);
                    } else {
                        log.warn("表 {}.{} FK警告: 父表 {}.{} 既无本次生成数据也无已有数据，FK值将为null",
                                tableMeta.getTableName(), col.getColumnName(),
                                col.getReferencedTable(), col.getReferencedColumn());
                    }
                }
            }
        }
    }

    /**
     * 并发预填充AI生成器的数据
     * 对所有需要AI生成的字段并行调用大模型，大幅提升生成速度
     */
    private void preFillLlmGenerators(String tableName, TableMetadata tableMeta,
                                       Map<String, FieldGenerator> generators, int totalRows,
                                       List<String> models) {
        // 收集所有需要AI生成的字段
        List<ColumnMetadata> llmColumns = tableMeta.getColumns().stream()
                .filter(col -> generators.get(col.getColumnName()) instanceof LlmBatchGenerator)
                .collect(Collectors.toList());

        if (llmColumns.isEmpty()) {
            return;
        }

        log.info("表 {} 开始并发预填充 {} 个字段的AI生成数据，目标行数: {}",
                tableName, llmColumns.size(), totalRows);

        long startTime = System.currentTimeMillis();

        // 为每个字段创建并发任务
        List<CompletableFuture<Void>> futures = llmColumns.stream()
                .map(col -> CompletableFuture.runAsync(() ->
                        preFillColumn(tableName, tableMeta, col, generators, totalRows, models), llmExecutor))
                .collect(Collectors.toList());

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("表 {} 并发预填充完成，耗时: {} ms", tableName, elapsed);
        } catch (Exception e) {
            log.error("表 {} 并发预填充异常: {}", tableName, e.getMessage(), e);
            // 如果有异常，回退到串行模式
            log.warn("表 {} 回退到串行预填充模式", tableName);
            for (ColumnMetadata col : llmColumns) {
                try {
                    preFillColumn(tableName, tableMeta, col, generators, totalRows, models);
                } catch (Exception ex) {
                    log.error("列 {}.{} 串行预填充失败: {}", tableName, col.getColumnName(), ex.getMessage());
                }
            }
        }
    }

    /**
     * 为单个字段预填充数据（可并发调用）
     */
    private void preFillColumn(String tableName, TableMetadata tableMeta,
                               ColumnMetadata col, Map<String, FieldGenerator> generators,
                               int totalRows, List<String> models) {
        try {
            FieldGenerator gen = generators.get(col.getColumnName());
            if (!(gen instanceof LlmBatchGenerator)) {
                return;
            }

            LlmBatchGenerator llmGen = (LlmBatchGenerator) gen;
            int needed = totalRows;

            while (llmGen.needsMoreValues(needed)) {
                int batchSize = Math.min(llmBatchSize, needed);
                // 优先使用用户在规则配置中输入的 description，其次是列注释，最后是列名
                String description = llmGen.getDescription();
                if (description == null || description.isEmpty()) {
                    description = col.getComment() != null ? col.getComment() : col.getColumnName();
                }

                log.debug("开始生成表 {}.{} 的批次数据，批次大小: {}", tableName, col.getColumnName(), batchSize);
                List<Object> values = llmService.generateBatchValues(tableName, col, description, batchSize, models);

                if (values.isEmpty()) {
                    // AI generation failed or returned empty - fallback to default generator
                    log.warn("AI生成失败，使用默认规则生成列 {}.{} 的数据", tableName, col.getColumnName());
                    DefaultGenerator defaultGen = new DefaultGenerator(
                            col.getDataType(), col.getMaxLength(), col.isNullable());
                    // Fill remaining values using default generator
                    for (int i = 0; i < needed; i++) {
                        llmGen.addValues(Collections.singletonList(
                                defaultGen.generate(null)));
                    }
                    break;
                }

                llmGen.addValues(values);
                needed -= values.size();
                if (needed <= 0) break;
            }

            log.debug("列 {}.{} 预填充完成，已生成 {} 条数据",
                    tableName, col.getColumnName(), llmGen.getPoolSize());

        } catch (Exception e) {
            log.error("列 {}.{} 预填充异常: {}", tableName, col.getColumnName(), e.getMessage(), e);
            // 异常时使用默认生成器
            LlmBatchGenerator llmGen = (LlmBatchGenerator) generators.get(col.getColumnName());
            if (llmGen != null && llmGen.getPoolSize() == 0) {
                log.warn("列 {}.{} 使用默认生成器作为异常回退", tableName, col.getColumnName());
                DefaultGenerator defaultGen = new DefaultGenerator(
                        col.getDataType(), col.getMaxLength(), col.isNullable());
                for (int i = 0; i < totalRows; i++) {
                    llmGen.addValues(Collections.singletonList(
                            defaultGen.generate(null)));
                }
            }
        }
    }

    private List<Map<String, Object>> generateRows(TableMetadata tableMeta,
                                                     Map<String, FieldGenerator> generators, int rowCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, Object> context = new HashMap<>();
            context.put("rowIndex", i);
            context.put("currentRow", row);

            for (ColumnMetadata col : tableMeta.getColumns()) {
                FieldGenerator gen = generators.get(col.getColumnName());
                if (gen != null) {
                    Object value = gen.generate(context);
                    row.put(col.getColumnName(), value);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private void trackGeneratedPks(TableMetadata tableMeta, List<Map<String, Object>> rows,
                                    Map<String, List<Object>> generatedPkMap) {
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (col.isPrimaryKey() && !col.isAutoIncrement()) {
                List<Object> pks = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    Object val = row.get(col.getColumnName());
                    if (val != null) pks.add(val);
                }
                if (!pks.isEmpty()) {
                    // 使用 "tableName.columnName" 规范化key，支持复合主键 + 大小写不敏感
                    String key = pkMapKey(tableMeta.getTableName(), col.getColumnName());
                    generatedPkMap.put(key, pks);
                    log.info("追踪PK: 表 {} 列 {} 生成了 {} 个非自增主键值, key={}",
                            tableMeta.getTableName(), col.getColumnName(), pks.size(), key);
                }
            }
        }
    }

    /**
     * 根据外键依赖关系将表分成多个层级。
     * 同一层级内的表互不依赖，可以并行生成数据。
     * 不同层级之间按顺序执行，确保父表数据先于子表生成。
     */
    private List<List<String>> buildDependencyLevels(SqlAnalysisResult analysisResult) {
        List<String> generationOrder = analysisResult.getGenerationOrder();
        Map<String, TableMetadata> metaMap = analysisResult.getTableMetadataMap();

        // 构建大小写不敏感的表名映射: lowercaseName -> originalName
        Map<String, String> normalizedToOriginal = new HashMap<>();
        for (String tableName : generationOrder) {
            normalizedToOriginal.put(tableName.toLowerCase(), tableName);
        }
        Set<String> normalizedTableSet = normalizedToOriginal.keySet();

        // 构建每个表的依赖集合（仅在当前生成范围内的依赖），使用规范化名称比较
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String tableName : generationOrder) {
            Set<String> deps = new HashSet<>();
            TableMetadata meta = metaMap.get(tableName);
            if (meta != null) {
                for (ColumnMetadata col : meta.getColumns()) {
                    if (col.getReferencedTable() != null) {
                        String refNormalized = col.getReferencedTable().toLowerCase();
                        if (normalizedTableSet.contains(refNormalized)
                                && !refNormalized.equals(tableName.toLowerCase())) {
                            // 存储原始表名作为依赖（从normalizedToOriginal映射回来）
                            deps.add(normalizedToOriginal.get(refNormalized));
                        }
                    }
                }
            }
            dependencies.put(tableName, deps);
        }

        // 按层级分组（拓扑排序分层）
        List<List<String>> levels = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        while (processed.size() < generationOrder.size()) {
            List<String> currentLevel = new ArrayList<>();
            for (String tableName : generationOrder) {
                if (processed.contains(tableName)) continue;
                // 当前表的所有依赖都已经在之前的层级中处理过
                Set<String> deps = dependencies.get(tableName);
                if (deps == null || processed.containsAll(deps)) {
                    currentLevel.add(tableName);
                }
            }

            if (currentLevel.isEmpty()) {
                // 防止死循环（循环依赖情况），将剩余表全部放入一个层级
                log.warn("检测到循环依赖，将剩余表放入同一层级");
                for (String tableName : generationOrder) {
                    if (!processed.contains(tableName)) {
                        currentLevel.add(tableName);
                    }
                }
            }

            levels.add(currentLevel);
            processed.addAll(currentLevel);
        }

        log.info("表依赖分层结果: 共 {} 层", levels.size());
        for (int i = 0; i < levels.size(); i++) {
            log.info("  第 {} 层: {}", i, levels.get(i));
        }
        return levels;
    }

    private List<Object> fetchExistingKeys(Long connectionId, String tableName, String columnName) {
        List<Object> keys = new ArrayList<>();
        try (Connection conn = connectionService.getConnection(connectionId)) {
            String sql = "SELECT `" + columnName + "` FROM `" + tableName + "` LIMIT 1000";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getObject(1));
                }
            }
        } catch (Exception e) {
            log.warn("获取表 {} 的已有主键失败: {}", tableName, e.getMessage());
        }
        return keys;
    }
}

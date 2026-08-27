package com.testdatagen.engine;

import com.testdatagen.engine.impl.DefaultGenerator;
import com.testdatagen.engine.impl.ForeignKeyGenerator;
import com.testdatagen.engine.impl.LlmBatchGenerator;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.DataGenRequest;
import com.testdatagen.model.dto.DataPreviewResponse;
import com.testdatagen.model.dto.RegenerateColumnsResponse;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.exception.TaskCancelledException;
import com.testdatagen.service.*;
import com.testdatagen.util.SqlTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class DataGenerationPipeline {

    private static final Logger log = LoggerFactory.getLogger(DataGenerationPipeline.class);

    /**
     * execute() 方法的返回值：同时包含行计数和生成的原始数据。
     */
    public static class ExecutionResult {
        private final Map<String, Integer> rowCounts;
        private final Map<String, List<Map<String, Object>>> tableData;

        public ExecutionResult(Map<String, Integer> rowCounts, Map<String, List<Map<String, Object>>> tableData) {
            this.rowCounts = rowCounts;
            this.tableData = tableData;
        }

        public Map<String, Integer> getRowCounts() { return rowCounts; }
        public Map<String, List<Map<String, Object>>> getTableData() { return tableData; }
    }

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
     * 带进度回调的预览方法（异步版本）。
     * 在每个依赖层级的表处理前后调用 callback 上报进度，并在关键节点检查取消标志。
     */
    public DataPreviewResponse preview(DataGenRequest request, SqlAnalysisResult analysisResult,
                                        ProgressCallback callback) {
        DataPreviewResponse response = new DataPreviewResponse();
        Map<String, List<Map<String, Object>>> tableData = new ConcurrentHashMap<>();
        int previewRows = request.getRowCount();

        Map<String, List<Object>> generatedPkMap = new ConcurrentHashMap<>();

        List<List<String>> levels = buildDependencyLevels(analysisResult);

        // 计算表总数
        int totalTables = 0;
        for (List<String> level : levels) {
            totalTables += level.size();
        }
        int tableIndex = 0;

        for (List<String> level : levels) {
            // 取消检查点
            checkCancelled(callback);

            if (level.size() == 1) {
                String tableName = level.get(0);
                if (callback != null) callback.onTableStart(tableName, tableIndex, totalTables);
                generateTablePreviewData(tableName, request, analysisResult, generatedPkMap, tableData, previewRows);
                if (callback != null) callback.onTableComplete(tableName, tableIndex, totalTables);
                tableIndex++;
            } else {
                // 多表并行 - 先通知所有表开始
                final int baseIndex = tableIndex;
                final int total = totalTables;
                for (int i = 0; i < level.size(); i++) {
                    if (callback != null) callback.onTableStart(level.get(i), baseIndex + i, total);
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < level.size(); i++) {
                    final String tableName = level.get(i);
                    final int idx = baseIndex + i;
                    futures.add(CompletableFuture.runAsync(() -> {
                        generateTablePreviewData(tableName, request, analysisResult, generatedPkMap, tableData, previewRows);
                        if (callback != null) callback.onTableComplete(tableName, idx, total);
                    }, llmExecutor));
                }

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (java.util.concurrent.CompletionException e) {
                    if (e.getCause() instanceof TaskCancelledException) {
                        throw (TaskCancelledException) e.getCause();
                    }
                    log.error("并行生成预览数据异常: {}", e.getMessage(), e);
                    throw new RuntimeException("生成预览数据失败: " + e.getMessage(), e);
                }
                tableIndex += level.size();
            }

            // 每层处理完再检查一次
            checkCancelled(callback);
        }

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
     * 取消检查点：若 callback 报告已取消，则抛出异常终止流程。
     */
    private void checkCancelled(ProgressCallback callback) {
        if (callback != null && callback.isCancelled()) {
            throw new TaskCancelledException();
        }
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

        trackGeneratedPks(tableMeta, rows, generatedPkMap, analysisResult);
    }

    /**
     * Generate data and write to DB in a single transaction.
     * 同层级的表并行生成数据，写入时在同一事务下顺序执行。
     * All tables are written within the same transaction - if any table fails, all writes are rolled back.
     * Returns ExecutionResult with row counts and generated data rows (capped at 200 rows per table for snapshot).
     */
    public ExecutionResult execute(DataGenRequest request, SqlAnalysisResult analysisResult) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, List<Object>> generatedPkMap = new ConcurrentHashMap<>();
        Map<String, List<Map<String, Object>>> snapshotData = new LinkedHashMap<>();

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

        // 收集数据快照（每表最多 200 行）
        for (TableWriteData data : allTableData) {
            List<Map<String, Object>> rows = data.rows;
            if (rows.size() > 200) {
                rows = rows.subList(0, 200);
            }
            snapshotData.put(data.tableName, new ArrayList<>(rows));
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

        return new ExecutionResult(result, snapshotData);
    }

    /**
     * 仅重新生成指定列的数据，保持其他列数据不变。
     * 支持单列或多列批量重新生成。
     */
    public RegenerateColumnsResponse regenerateColumns(String tableName, List<String> columns, int rowCount,
                                                        List<DataGenRequest.FieldRuleRequest> fieldRules,
                                                        List<String> models,
                                                        SqlAnalysisResult analysisResult,
                                                        Map<String, List<Map<String, Object>>> existingData) {
        RegenerateColumnsResponse response = new RegenerateColumnsResponse(tableName);

        TableMetadata tableMeta = analysisResult.getTableMetadataMap().get(tableName);
        if (tableMeta == null) {
            response.addWarning("未找到表 " + tableName + " 的元数据");
            return response;
        }

        // 过滤自增列
        Set<String> targetColumns = new LinkedHashSet<>();
        for (String col : columns) {
            ColumnMetadata colMeta = findColumnMeta(tableMeta, col);
            if (colMeta == null) {
                response.addWarning("未找到列 " + tableName + "." + col);
                continue;
            }
            if (colMeta.isAutoIncrement()) {
                response.addWarning("列 " + col + " 为自增列，跳过重新生成");
                continue;
            }
            targetColumns.add(col);
        }

        if (targetColumns.isEmpty()) {
            return response;
        }

        // 为目标列匹配生成器
        Map<String, FieldGenerator> generators = ruleMatchingEngine.matchRules(
                tableName, tableMeta.getColumns(), fieldRules, null);

        // 为FK列设置parentKeys（从existingData中提取）
        setupForeignKeyGeneratorsFromExistingData(tableMeta, generators, existingData, targetColumns);

        // 为LLM列预填充数据（仅目标列中的LLM列）
        preFillLlmGeneratorsForColumns(tableName, tableMeta, generators, rowCount, models, targetColumns);

        // 获取当前表的已有数据（用于context）
        List<Map<String, Object>> existingRows = existingData != null ? existingData.get(tableName) : null;

        // 逐行生成目标列数据
        for (String col : targetColumns) {
            FieldGenerator gen = generators.get(col);
            if (gen == null) {
                response.addWarning("列 " + col + " 无法创建生成器，跳过");
                continue;
            }

            List<Object> values = new ArrayList<>();
            for (int i = 0; i < rowCount; i++) {
                Map<String, Object> context = new HashMap<>();
                context.put("rowIndex", i);
                // 将当前行已有数据放入context，供生成器参考
                if (existingRows != null && i < existingRows.size()) {
                    context.put("currentRow", existingRows.get(i));
                } else {
                    context.put("currentRow", new HashMap<>());
                }
                values.add(truncateIfNeeded(gen.generate(context), findColumnMeta(tableMeta, col)));
            }
            response.putColumnValues(col, values);
        }

        log.info("表 {} 重新生成 {} 列数据完成，每列 {} 行", tableName, targetColumns.size(), rowCount);
        return response;
    }

    /**
     * 从existingData中为FK列设置parentKeys
     */
    private void setupForeignKeyGeneratorsFromExistingData(TableMetadata tableMeta,
                                                            Map<String, FieldGenerator> generators,
                                                            Map<String, List<Map<String, Object>>> existingData,
                                                            Set<String> targetColumns) {
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (!targetColumns.contains(col.getColumnName())) continue;
            if (col.getReferencedTable() == null) continue;
            if (!(generators.get(col.getColumnName()) instanceof ForeignKeyGenerator)) continue;

            ForeignKeyGenerator fkGen = (ForeignKeyGenerator) generators.get(col.getColumnName());
            String refTable = col.getReferencedTable();
            String refCol = col.getReferencedColumn();

            // 从existingData中提取父表的引用列值
            if (existingData != null && existingData.containsKey(refTable)) {
                List<Map<String, Object>> parentRows = existingData.get(refTable);
                List<Object> parentKeys = new ArrayList<>();
                for (Map<String, Object> row : parentRows) {
                    Object val = row.get(refCol);
                    if (val != null) parentKeys.add(val);
                }
                if (!parentKeys.isEmpty()) {
                    fkGen.setParentKeys(parentKeys);
                    log.info("重新生成: 表 {}.{} FK注入, 从existingData父表 {}.{} 获取 {} 个parentKeys",
                            tableMeta.getTableName(), col.getColumnName(), refTable, refCol, parentKeys.size());
                } else {
                    log.warn("重新生成: 表 {}.{} FK警告, existingData中父表 {} 的 {} 列值为空",
                            tableMeta.getTableName(), col.getColumnName(), refTable, refCol);
                }
            } else {
                log.warn("重新生成: 表 {}.{} FK警告, existingData中未找到父表 {}",
                        tableMeta.getTableName(), col.getColumnName(), refTable);
            }
        }
    }

    /**
     * 仅为目标列中的LLM生成器预填充数据
     */
    private void preFillLlmGeneratorsForColumns(String tableName, TableMetadata tableMeta,
                                                  Map<String, FieldGenerator> generators, int totalRows,
                                                  List<String> models, Set<String> targetColumns) {
        List<ColumnMetadata> llmColumns = tableMeta.getColumns().stream()
                .filter(col -> targetColumns.contains(col.getColumnName()))
                .filter(col -> generators.get(col.getColumnName()) instanceof LlmBatchGenerator)
                .collect(Collectors.toList());

        if (llmColumns.isEmpty()) return;

        log.info("重新生成: 表 {} 预填充 {} 个LLM列", tableName, llmColumns.size());

        List<CompletableFuture<Void>> futures = llmColumns.stream()
                .map(col -> CompletableFuture.runAsync(() ->
                        preFillColumn(tableName, tableMeta, col, generators, totalRows, models), llmExecutor))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("重新生成: LLM预填充异常: {}", e.getMessage(), e);
        }
    }

    private ColumnMetadata findColumnMeta(TableMetadata tableMeta, String columnName) {
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (col.getColumnName().equalsIgnoreCase(columnName)) {
                return col;
            }
        }
        return null;
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

        // Track PKs (追踪被FK引用的列值)
        trackGeneratedPks(tableMeta, rows, generatedPkMap, analysisResult);

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
                            col.getDataType(), col.getMaxLength(), col.isNullable(), col.getNumericScale());
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
                        col.getDataType(), col.getMaxLength(), col.isNullable(), col.getNumericScale());
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
                    row.put(col.getColumnName(), truncateIfNeeded(value, col));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 根据列的 maxLength 约束截断字符串值，防止写入时 Data too long 错误；
     * 同时对 DECIMAL 列的数值做范围钳制，防止 Out of range 错误。
     */
    private Object truncateIfNeeded(Object value, ColumnMetadata col) {
        if (value instanceof String) {
            String str = (String) value;
            Integer maxLen = col.getMaxLength();
            if (maxLen == null || maxLen <= 0 || str.length() <= maxLen) return value;
            String truncated = str.substring(0, maxLen);
            log.debug("截断字段 {}: 原长度={}, maxLength={}, 截断后='{}'",
                    col.getColumnName(), str.length(), maxLen,
                    truncated.length() > 20 ? truncated.substring(0, 20) + "..." : truncated);
            return truncated;
        }
        if (value instanceof Number) {
            return clampDecimalIfNeeded((Number) value, col);
        }
        return value;
    }

    /**
     * DECIMAL 列数值范围钳制：已知精度/小数位时，超出列上限的值钳制到边界，
     * 避免写入时 Data truncation: Out of range（兼底 LLM/REGEX/RANGE 等生成器的越界输出）。
     */
    private Object clampDecimalIfNeeded(Number value, ColumnMetadata col) {
        Integer precision = col.getMaxLength();
        if (precision == null || precision <= 0) return value;
        if (!"BigDecimal".equals(SqlTypeMapper.toJavaType(col.getDataType()))) return value;

        int scale = col.getNumericScale() != null && col.getNumericScale() >= 0
                ? Math.min(col.getNumericScale(), precision) : 0;
        BigDecimal max = BigDecimal.TEN.pow(precision - scale).subtract(BigDecimal.ONE.movePointLeft(scale));

        BigDecimal bd;
        try {
            bd = new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return value;
        }
        bd = bd.setScale(scale, RoundingMode.HALF_UP);
        if (bd.abs().compareTo(max) > 0) {
            BigDecimal clamped = bd.signum() < 0 ? max.negate() : max;
            log.warn("数值超出列范围，已钳制: 列 {} 值 {} -> {}, 列定义 DECIMAL({},{})",
                    col.getColumnName(), bd, clamped, precision, scale);
            return clamped;
        }
        return bd;
    }

    private void trackGeneratedPks(TableMetadata tableMeta, List<Map<String, Object>> rows,
                                    Map<String, List<Object>> generatedPkMap,
                                    SqlAnalysisResult analysisResult) {
        String currentTable = tableMeta.getTableName().toLowerCase();

        // 收集当前表中需要被追踪的列：PK列 + 被其他表FK引用的列
        Set<String> columnsToTrack = new HashSet<>();
        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (col.isPrimaryKey()) {
                columnsToTrack.add(col.getColumnName().toLowerCase());
            }
        }
        // 从所有表的FK关系中找到引用当前表的列
        Map<String, TableMetadata> metaMap = analysisResult.getTableMetadataMap();
        if (metaMap != null) {
            for (TableMetadata otherMeta : metaMap.values()) {
                if (otherMeta.getColumns() == null) continue;
                for (ColumnMetadata otherCol : otherMeta.getColumns()) {
                    if (otherCol.getReferencedTable() != null
                            && otherCol.getReferencedTable().equalsIgnoreCase(currentTable)
                            && otherCol.getReferencedColumn() != null) {
                        columnsToTrack.add(otherCol.getReferencedColumn().toLowerCase());
                    }
                }
            }
        }

        if (columnsToTrack.isEmpty()) return;

        for (ColumnMetadata col : tableMeta.getColumns()) {
            if (!columnsToTrack.contains(col.getColumnName().toLowerCase())) continue;

            String key = pkMapKey(tableMeta.getTableName(), col.getColumnName());

            if (col.isAutoIncrement()) {
                // 自增列：生成递增序列值用于子表FK引用
                List<Object> pks = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    long seqVal = i + 1L;
                    pks.add(seqVal);
                    rows.get(i).put(col.getColumnName(), seqVal);
                }
                generatedPkMap.put(key, pks);
                log.info("追踪FK引用列: 表 {} 列 {} 生成 {} 个自增序列值, key={}",
                        tableMeta.getTableName(), col.getColumnName(), pks.size(), key);
            } else {
                // 非自增列：从已生成的行数据中提取
                List<Object> pks = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    Object val = row.get(col.getColumnName());
                    if (val != null) pks.add(val);
                }
                if (!pks.isEmpty()) {
                    generatedPkMap.put(key, pks);
                    log.info("追踪FK引用列: 表 {} 列 {} 提取 {} 个已生成值, key={}",
                            tableMeta.getTableName(), col.getColumnName(), pks.size(), key);
                } else {
                    log.warn("追踪FK引用列: 表 {} 列 {} 生成值为空, 子表FK将回退到数据库查询",
                            tableMeta.getTableName(), col.getColumnName());
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

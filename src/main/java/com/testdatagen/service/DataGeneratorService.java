package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.testdatagen.engine.DataGenerationPipeline;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.DataGenRequest;
import com.testdatagen.model.dto.DataPreviewResponse;
import com.testdatagen.model.dto.RegenerateColumnsRequest;
import com.testdatagen.model.dto.RegenerateColumnsResponse;
import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.model.enums.TaskStatus;
import com.testdatagen.repository.GenerationTaskRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class DataGeneratorService {

    private final SqlParserService sqlParserService;
    private final ConnectionService connectionService;
    private final RuleMatchingEngine ruleMatchingEngine;
    private final LlmService llmService;
    private final DataWriterService dataWriterService;
    private final GenerationTaskRepository taskRepository;
    private final RuleService ruleService;
    private final Executor dataGenExecutor;

    @Value("${app.generation.llm-batch-size:50}")
    private int llmBatchSize;

    @Value("${app.generation.insert-batch-size:500}")
    private int insertBatchSize;

    public DataGeneratorService(SqlParserService sqlParserService,
                                 ConnectionService connectionService,
                                 RuleMatchingEngine ruleMatchingEngine,
                                 LlmService llmService,
                                 DataWriterService dataWriterService,
                                 GenerationTaskRepository taskRepository,
                                 RuleService ruleService,
                                 @Qualifier("dataGenExecutor") Executor dataGenExecutor) {
        this.sqlParserService = sqlParserService;
        this.connectionService = connectionService;
        this.ruleMatchingEngine = ruleMatchingEngine;
        this.llmService = llmService;
        this.dataWriterService = dataWriterService;
        this.taskRepository = taskRepository;
        this.ruleService = ruleService;
        this.dataGenExecutor = dataGenExecutor;
    }

    public DataPreviewResponse preview(DataGenRequest request) {
        SqlAnalysisResult analysis = sqlParserService.analyze(request.getConnectionId(), request.getSql());
        DataGenerationPipeline pipeline = createPipeline();
        return pipeline.preview(request, analysis);
    }

    /**
     * 仅重新生成指定列的数据，保持其他列不变。
     */
    public RegenerateColumnsResponse regenerateColumns(RegenerateColumnsRequest request) {
        SqlAnalysisResult analysis = sqlParserService.analyze(request.getConnectionId(), request.getSql());
        DataGenerationPipeline pipeline = createPipeline();
        return pipeline.regenerateColumns(
                request.getTableName(),
                request.getColumns(),
                request.getRowCount(),
                request.getFieldRules(),
                request.getModels(),
                analysis,
                request.getExistingData()
        );
    }

    public GenerationTask execute(DataGenRequest request) {
        GenerationTask task = new GenerationTask();
        task.setConnectionId(request.getConnectionId());
        task.setInputSql(request.getSql());
        task.setRowCount(request.getRowCount());
        task.setStatus(TaskStatus.RUNNING);
        // 保存规则快照
        if (request.getFieldRules() != null && !request.getFieldRules().isEmpty()) {
            task.setRulesSnapshot(JSON.toJSONString(request.getFieldRules()));
        }
        task = taskRepository.save(task);

        try {
            SqlAnalysisResult analysis = sqlParserService.analyze(request.getConnectionId(), request.getSql());
            // 保存分析快照（表结构 + 关联关系 + 生成顺序）
            task.setAnalysisSnapshot(JSON.toJSONString(analysis));

            DataGenerationPipeline pipeline = createPipeline();
            Map<String, Integer> result = pipeline.execute(request, analysis);

            int totalRows = result.values().stream().mapToInt(Integer::intValue).sum();
            task.setStatus(TaskStatus.SUCCESS);
            task.setRowsGenerated(totalRows);
            task.setCompletedAt(LocalDateTime.now());

            // 写入成功后保存字段规则历史
            ruleService.saveFieldRuleHistory(request.getFieldRules(), request.getSqlScriptId());
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
        }

        return taskRepository.save(task);
    }

    /**
     * 将预览生成的数据直接写入数据库，无需重新生成。
     * 所有表在同一事务下写入，任意失败全部回滚。
     */
    public GenerationTask writePreviewData(Long connectionId, String sql,
                                            DataPreviewResponse previewData,
                                            List<DataGenRequest.FieldRuleRequest> fieldRules,
                                            Long sqlScriptId) {
        GenerationTask task = new GenerationTask();
        task.setConnectionId(connectionId);
        task.setInputSql(sql);
        task.setStatus(TaskStatus.RUNNING);
        // 保存规则快照
        if (fieldRules != null && !fieldRules.isEmpty()) {
            task.setRulesSnapshot(JSON.toJSONString(fieldRules));
        }
        task = taskRepository.save(task);

        try {
            SqlAnalysisResult analysis = sqlParserService.analyze(connectionId, sql);
            // 保存分析快照（表结构 + 关联关系 + 生成顺序）
            task.setAnalysisSnapshot(JSON.toJSONString(analysis));

            int totalRows = 0;

            // 构建所有表的写入任务
            List<DataWriterService.TableWriteTask> writeTasks = new ArrayList<>();
            for (String tableName : previewData.getGenerationOrder()) {
                List<Map<String, Object>> rows = previewData.getTableData().get(tableName);
                if (rows == null || rows.isEmpty()) continue;

                // 从 analysis 获取列元信息，排除自增列
                TableMetadata tableMeta = analysis.getTableMetadataMap().get(tableName);
                List<String> columns = new ArrayList<>();
                if (tableMeta != null) {
                    for (ColumnMetadata col : tableMeta.getColumns()) {
                        if (!col.isAutoIncrement() && rows.get(0).containsKey(col.getColumnName())) {
                            columns.add(col.getColumnName());
                        }
                    }
                } else {
                    columns.addAll(rows.get(0).keySet());
                }

                writeTasks.add(new DataWriterService.TableWriteTask(tableName, columns, rows));
                totalRows += rows.size();
            }

            // 在同一事务中写入所有表
            dataWriterService.writeAllTablesInTransaction(connectionId, writeTasks, insertBatchSize);

            task.setRowCount(totalRows);
            task.setStatus(TaskStatus.SUCCESS);
            task.setRowsGenerated(totalRows);
            task.setCompletedAt(LocalDateTime.now());

            // 写入成功后保存字段规则历史
            ruleService.saveFieldRuleHistory(fieldRules, sqlScriptId);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
        }

        return taskRepository.save(task);
    }

    private DataGenerationPipeline createPipeline() {
        return new DataGenerationPipeline(connectionService, ruleMatchingEngine,
                llmService, dataWriterService, llmBatchSize, insertBatchSize, dataGenExecutor);
    }
}

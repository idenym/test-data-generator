package com.testdatagen.controller;

import com.testdatagen.config.OpenAiConfig.ModelConfig;
import com.testdatagen.config.OpenAiConfig.OpenAiProperties;
import com.testdatagen.model.dto.DataGenRequest;
import com.testdatagen.model.dto.DataPreviewResponse;
import com.testdatagen.model.entity.GenerationTask;
import com.testdatagen.service.DataGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/generate")
public class DataGenController {

    private final DataGeneratorService dataGeneratorService;
    private final OpenAiProperties openAiProperties;

    public DataGenController(DataGeneratorService dataGeneratorService, OpenAiProperties openAiProperties) {
        this.dataGeneratorService = dataGeneratorService;
        this.openAiProperties = openAiProperties;
    }

    /**
     * 获取可用模型列表（从配置文件读取）
     */
    @GetMapping("/models")
    public ResponseEntity<List<Map<String, String>>> getAvailableModels() {
        List<Map<String, String>> models = openAiProperties.getModels().entrySet().stream()
                .map(entry -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("id", entry.getKey());
                    m.put("name", entry.getValue().getName() != null ? entry.getValue().getName() : entry.getKey());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }

    @PostMapping("/preview")
    public DataPreviewResponse preview(@Valid @RequestBody DataGenRequest request) {
        return dataGeneratorService.preview(request);
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@Valid @RequestBody DataGenRequest request) {
        GenerationTask task = dataGeneratorService.execute(request);
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus().name());
        result.put("rowsGenerated", task.getRowsGenerated());
        result.put("errorMessage", task.getErrorMessage());
        return ResponseEntity.ok(result);
    }

    /**
     * 将预览数据直接写入数据库，无需重新生成
     */
    @PostMapping("/write")
    public ResponseEntity<Map<String, Object>> writePreviewData(@RequestBody WriteRequest request) {
        DataPreviewResponse previewData = new DataPreviewResponse();
        previewData.setTableData(request.getTableData());
        previewData.setGenerationOrder(request.getGenerationOrder());

        GenerationTask task = dataGeneratorService.writePreviewData(
                request.getConnectionId(), request.getSql(), previewData, request.getFieldRules(),
                request.getSqlScriptId());

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus().name());
        result.put("rowsGenerated", task.getRowsGenerated());
        result.put("errorMessage", task.getErrorMessage());
        return ResponseEntity.ok(result);
    }

    public static class WriteRequest {
        private Long connectionId;
        private String sql;
        private Map<String, List<Map<String, Object>>> tableData;
        private List<String> generationOrder;
        private List<DataGenRequest.FieldRuleRequest> fieldRules;
        private Long sqlScriptId;

        public Long getConnectionId() { return connectionId; }
        public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public Map<String, List<Map<String, Object>>> getTableData() { return tableData; }
        public void setTableData(Map<String, List<Map<String, Object>>> tableData) { this.tableData = tableData; }
        public List<String> getGenerationOrder() { return generationOrder; }
        public void setGenerationOrder(List<String> generationOrder) { this.generationOrder = generationOrder; }
        public List<DataGenRequest.FieldRuleRequest> getFieldRules() { return fieldRules; }
        public void setFieldRules(List<DataGenRequest.FieldRuleRequest> fieldRules) { this.fieldRules = fieldRules; }
        public Long getSqlScriptId() { return sqlScriptId; }
        public void setSqlScriptId(Long sqlScriptId) { this.sqlScriptId = sqlScriptId; }
    }
}

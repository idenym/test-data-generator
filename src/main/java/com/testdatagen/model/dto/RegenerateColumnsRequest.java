package com.testdatagen.model.dto;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class RegenerateColumnsRequest {

    @NotNull(message = "连接ID不能为空")
    private Long connectionId;

    @NotNull(message = "SQL不能为空")
    private String sql;

    @NotNull(message = "表名不能为空")
    private String tableName;

    @NotNull(message = "列名列表不能为空")
    private List<String> columns;

    private int rowCount = 100;

    private List<DataGenRequest.FieldRuleRequest> fieldRules;

    private List<String> models;

    private Map<String, List<Map<String, Object>>> existingData;

    private Long ruleSetId;

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public List<DataGenRequest.FieldRuleRequest> getFieldRules() { return fieldRules; }
    public void setFieldRules(List<DataGenRequest.FieldRuleRequest> fieldRules) { this.fieldRules = fieldRules; }
    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }
    public Map<String, List<Map<String, Object>>> getExistingData() { return existingData; }
    public void setExistingData(Map<String, List<Map<String, Object>>> existingData) { this.existingData = existingData; }
    public Long getRuleSetId() { return ruleSetId; }
    public void setRuleSetId(Long ruleSetId) { this.ruleSetId = ruleSetId; }
}

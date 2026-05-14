package com.testdatagen.model.dto;

import com.testdatagen.model.enums.RuleType;

import javax.validation.constraints.NotNull;
import java.util.List;

public class DataGenRequest {

    @NotNull(message = "连接ID不能为空")
    private Long connectionId;

    @NotNull(message = "SQL不能为空")
    private String sql;

    private int rowCount = 100;

    private List<FieldRuleRequest> fieldRules;

    private Long ruleSetId;

    private boolean saveRules = false;

    private List<String> models;

    private Long sqlScriptId;

    public static class FieldRuleRequest {
        private String tableName;
        private String columnName;
        private RuleType ruleType;
        private String ruleConfig;
        private String description;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public RuleType getRuleType() { return ruleType; }
        public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }
        public String getRuleConfig() { return ruleConfig; }
        public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public List<FieldRuleRequest> getFieldRules() { return fieldRules; }
    public void setFieldRules(List<FieldRuleRequest> fieldRules) { this.fieldRules = fieldRules; }
    public Long getRuleSetId() { return ruleSetId; }
    public void setRuleSetId(Long ruleSetId) { this.ruleSetId = ruleSetId; }
    public boolean isSaveRules() { return saveRules; }
    public void setSaveRules(boolean saveRules) { this.saveRules = saveRules; }
    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }
    public Long getSqlScriptId() { return sqlScriptId; }
    public void setSqlScriptId(Long sqlScriptId) { this.sqlScriptId = sqlScriptId; }
}

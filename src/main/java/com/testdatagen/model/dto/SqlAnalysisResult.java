package com.testdatagen.model.dto;

import java.util.List;
import java.util.Map;

public class SqlAnalysisResult {
    private List<String> tables;
    private List<String> generationOrder;
    private Map<String, TableMetadata> tableMetadataMap;
    private List<RelationInfo> relations;
    private List<String> warnings;
    private List<WhereHint> whereHints;

    public static class RelationInfo {
        private String fromTable;
        private String fromColumn;
        private String toTable;
        private String toColumn;
        private String joinType;

        public String getFromTable() { return fromTable; }
        public void setFromTable(String fromTable) { this.fromTable = fromTable; }
        public String getFromColumn() { return fromColumn; }
        public void setFromColumn(String fromColumn) { this.fromColumn = fromColumn; }
        public String getToTable() { return toTable; }
        public void setToTable(String toTable) { this.toTable = toTable; }
        public String getToColumn() { return toColumn; }
        public void setToColumn(String toColumn) { this.toColumn = toColumn; }
        public String getJoinType() { return joinType; }
        public void setJoinType(String joinType) { this.joinType = joinType; }
    }

    /**
     * WHERE 子句中提取的字段约束提示，可预填为生成规则。
     * 例如 WHERE status = 1 → ENUM{"values":[1]}
     *      WHERE type IN ('A','B') → ENUM{"values":["A","B"]}
     */
    public static class WhereHint {
        private String tableName;
        private String columnName;
        private String ruleType;     // ENUM
        private String ruleConfig;   // JSON: {"values":[...]}
        private String description;  // 来源说明

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getRuleConfig() { return ruleConfig; }
        public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public List<String> getTables() { return tables; }
    public void setTables(List<String> tables) { this.tables = tables; }
    public List<String> getGenerationOrder() { return generationOrder; }
    public void setGenerationOrder(List<String> generationOrder) { this.generationOrder = generationOrder; }
    public Map<String, TableMetadata> getTableMetadataMap() { return tableMetadataMap; }
    public void setTableMetadataMap(Map<String, TableMetadata> tableMetadataMap) { this.tableMetadataMap = tableMetadataMap; }
    public List<RelationInfo> getRelations() { return relations; }
    public void setRelations(List<RelationInfo> relations) { this.relations = relations; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<WhereHint> getWhereHints() { return whereHints; }
    public void setWhereHints(List<WhereHint> whereHints) { this.whereHints = whereHints; }
}

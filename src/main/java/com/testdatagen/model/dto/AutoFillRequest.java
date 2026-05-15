package com.testdatagen.model.dto;

import java.util.List;

public class AutoFillRequest {

    private Long connectionId;
    private Long sqlScriptId;
    private List<TableInfo> tables;

    public static class TableInfo {
        private String tableName;
        private List<ColumnInfo> columns;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<ColumnInfo> getColumns() { return columns; }
        public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }
    }

    public static class ColumnInfo {
        private String columnName;
        private String comment;
        private String dataType;

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
    }

    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public Long getSqlScriptId() { return sqlScriptId; }
    public void setSqlScriptId(Long sqlScriptId) { this.sqlScriptId = sqlScriptId; }
    public List<TableInfo> getTables() { return tables; }
    public void setTables(List<TableInfo> tables) { this.tables = tables; }
}

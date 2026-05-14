package com.testdatagen.model.dto;

import java.util.List;

public class TableMetadata {
    private String tableName;
    private String tableComment;
    private List<ColumnMetadata> columns;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public List<ColumnMetadata> getColumns() { return columns; }
    public void setColumns(List<ColumnMetadata> columns) { this.columns = columns; }
}

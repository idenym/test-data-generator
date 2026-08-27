package com.testdatagen.model.dto;

public class ColumnMetadata {
    private String columnName;
    private String dataType;
    private String columnType;
    private Integer maxLength;
    /** 数值列的小数位数（DECIMAL/NUMERIC 的 scale） */
    private Integer numericScale;
    private boolean nullable;
    private boolean primaryKey;
    private boolean autoIncrement;
    private String defaultValue;
    private String comment;
    private String referencedTable;
    private String referencedColumn;

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public String getColumnType() { return columnType; }
    public void setColumnType(String columnType) { this.columnType = columnType; }
    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
    public Integer getNumericScale() { return numericScale; }
    public void setNumericScale(Integer numericScale) { this.numericScale = numericScale; }
    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
    public boolean isAutoIncrement() { return autoIncrement; }
    public void setAutoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getReferencedTable() { return referencedTable; }
    public void setReferencedTable(String referencedTable) { this.referencedTable = referencedTable; }
    public String getReferencedColumn() { return referencedColumn; }
    public void setReferencedColumn(String referencedColumn) { this.referencedColumn = referencedColumn; }
}

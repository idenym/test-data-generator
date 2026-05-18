package com.testdatagen.model.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegenerateColumnsResponse {

    private String tableName;

    private Map<String, List<Object>> columnData = new HashMap<>();

    private List<String> warnings = new ArrayList<>();

    public RegenerateColumnsResponse() {}

    public RegenerateColumnsResponse(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public Map<String, List<Object>> getColumnData() { return columnData; }
    public void setColumnData(Map<String, List<Object>> columnData) { this.columnData = columnData; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void putColumnValues(String columnName, List<Object> values) {
        this.columnData.put(columnName, values);
    }
}

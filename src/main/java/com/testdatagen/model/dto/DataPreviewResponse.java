package com.testdatagen.model.dto;

import java.util.List;
import java.util.Map;

public class DataPreviewResponse {
    private Map<String, List<Map<String, Object>>> tableData;
    private List<String> generationOrder;

    public Map<String, List<Map<String, Object>>> getTableData() { return tableData; }
    public void setTableData(Map<String, List<Map<String, Object>>> tableData) { this.tableData = tableData; }
    public List<String> getGenerationOrder() { return generationOrder; }
    public void setGenerationOrder(List<String> generationOrder) { this.generationOrder = generationOrder; }
}

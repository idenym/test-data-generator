package com.testdatagen.model.dto;

import com.testdatagen.model.enums.TaskStatus;

/**
 * 异步预览任务的状态响应 DTO。
 * 前端通过轮询此结构获取任务进度。
 */
public class PreviewStatusResponse {

    private String taskId;
    private TaskStatus status;
    private Progress progress;
    private String errorMessage;
    private DataPreviewResponse result;

    public static class Progress {
        private int completedTables;
        private int totalTables;
        private String currentTable;
        private int percentage;

        public Progress() {}

        public Progress(int completedTables, int totalTables, String currentTable) {
            this.completedTables = completedTables;
            this.totalTables = totalTables;
            this.currentTable = currentTable;
            this.percentage = totalTables > 0 ? (completedTables * 100 / totalTables) : 0;
        }

        public int getCompletedTables() { return completedTables; }
        public void setCompletedTables(int completedTables) { this.completedTables = completedTables; }
        public int getTotalTables() { return totalTables; }
        public void setTotalTables(int totalTables) { this.totalTables = totalTables; }
        public String getCurrentTable() { return currentTable; }
        public void setCurrentTable(String currentTable) { this.currentTable = currentTable; }
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
    }

    public PreviewStatusResponse() {}

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Progress getProgress() { return progress; }
    public void setProgress(Progress progress) { this.progress = progress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public DataPreviewResponse getResult() { return result; }
    public void setResult(DataPreviewResponse result) { this.result = result; }
}

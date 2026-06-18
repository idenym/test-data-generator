package com.testdatagen.model.entity;

import com.testdatagen.model.enums.TaskStatus;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "generation_task")
public class GenerationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(columnDefinition = "TEXT")
    private String inputSql;

    private Integer rowCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Integer rowsGenerated = 0;

    @Column(columnDefinition = "TEXT")
    private String rulesSnapshot;

    @Column(columnDefinition = "TEXT")
    private String analysisSnapshot;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String generatedDataSnapshot;

    private Boolean hasManualEdits = false;

    private Boolean hasRegeneration = false;

    @Column(columnDefinition = "TEXT")
    private String regeneratedColumns;

    private Integer editedCellCount = 0;

    private Integer regeneratedCellCount = 0;

    private Integer totalCellCount = 0;

    /** 任务类型：PREVIEW（仅预览）或 EXECUTE（写入数据库） */
    @Column(name = "task_type", length = 20)
    private String taskType = "EXECUTE";

    /** 已完成的表数量（用于进度跟踪） */
    @Column(name = "completed_tables")
    private Integer completedTables = 0;

    /** 需要处理的总表数量 */
    @Column(name = "total_tables")
    private Integer totalTables = 0;

    /** 当前正在处理的表名 */
    @Column(name = "current_table", length = 100)
    private String currentTable;

    /** 关联的内存预览任务ID（用于实时进度轮询） */
    @Column(name = "preview_task_id", length = 16)
    private String previewTaskId;

    @Column(name = "user_id")
    private Long userId;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long connectionId) { this.connectionId = connectionId; }
    public String getInputSql() { return inputSql; }
    public void setInputSql(String inputSql) { this.inputSql = inputSql; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Integer getRowsGenerated() { return rowsGenerated; }
    public void setRowsGenerated(Integer rowsGenerated) { this.rowsGenerated = rowsGenerated; }
    public String getRulesSnapshot() { return rulesSnapshot; }
    public void setRulesSnapshot(String rulesSnapshot) { this.rulesSnapshot = rulesSnapshot; }
    public String getAnalysisSnapshot() { return analysisSnapshot; }
    public void setAnalysisSnapshot(String analysisSnapshot) { this.analysisSnapshot = analysisSnapshot; }
    public String getGeneratedDataSnapshot() { return generatedDataSnapshot; }
    public void setGeneratedDataSnapshot(String generatedDataSnapshot) { this.generatedDataSnapshot = generatedDataSnapshot; }
    public Boolean getHasManualEdits() { return hasManualEdits; }
    public void setHasManualEdits(Boolean hasManualEdits) { this.hasManualEdits = hasManualEdits; }
    public Boolean getHasRegeneration() { return hasRegeneration; }
    public void setHasRegeneration(Boolean hasRegeneration) { this.hasRegeneration = hasRegeneration; }
    public String getRegeneratedColumns() { return regeneratedColumns; }
    public void setRegeneratedColumns(String regeneratedColumns) { this.regeneratedColumns = regeneratedColumns; }
    public Integer getEditedCellCount() { return editedCellCount; }
    public void setEditedCellCount(Integer editedCellCount) { this.editedCellCount = editedCellCount; }
    public Integer getRegeneratedCellCount() { return regeneratedCellCount; }
    public void setRegeneratedCellCount(Integer regeneratedCellCount) { this.regeneratedCellCount = regeneratedCellCount; }
    public Integer getTotalCellCount() { return totalCellCount; }
    public void setTotalCellCount(Integer totalCellCount) { this.totalCellCount = totalCellCount; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Integer getCompletedTables() { return completedTables; }
    public void setCompletedTables(Integer completedTables) { this.completedTables = completedTables; }
    public Integer getTotalTables() { return totalTables; }
    public void setTotalTables(Integer totalTables) { this.totalTables = totalTables; }
    public String getCurrentTable() { return currentTable; }
    public void setCurrentTable(String currentTable) { this.currentTable = currentTable; }
    public String getPreviewTaskId() { return previewTaskId; }
    public void setPreviewTaskId(String previewTaskId) { this.previewTaskId = previewTaskId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}

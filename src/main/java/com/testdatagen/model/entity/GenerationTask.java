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
}

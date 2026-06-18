package com.testdatagen.model.entity;

import com.testdatagen.model.enums.RuleType;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_rule_history",
        indexes = {
                @Index(name = "idx_table_column", columnList = "tableName, columnName"),
                @Index(name = "idx_script_table_column", columnList = "sqlScriptId, tableName, columnName")
        })
public class FieldRuleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Long sqlScriptId;

    @Column(nullable = false, length = 200)
    private String tableName;

    @Column(nullable = false, length = 200)
    private String columnName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleType ruleType;

    @Column(columnDefinition = "TEXT")
    private String ruleConfig;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer usedCount = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "user_id")
    private Long userId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUsedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSqlScriptId() { return sqlScriptId; }
    public void setSqlScriptId(Long sqlScriptId) { this.sqlScriptId = sqlScriptId; }
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
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}

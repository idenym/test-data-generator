package com.testdatagen.model.entity;

import com.testdatagen.model.enums.RuleType;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_rule")
public class FieldRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_set_id")
    private Long ruleSetId;

    @Column(length = 200)
    private String tablePattern;

    @Column(length = 200)
    private String columnPattern;

    @Column(length = 100)
    private String dataTypePattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleType ruleType;

    @Column(columnDefinition = "TEXT")
    private String ruleConfig;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(length = 500)
    private String description;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleSetId() { return ruleSetId; }
    public void setRuleSetId(Long ruleSetId) { this.ruleSetId = ruleSetId; }
    public String getTablePattern() { return tablePattern; }
    public void setTablePattern(String tablePattern) { this.tablePattern = tablePattern; }
    public String getColumnPattern() { return columnPattern; }
    public void setColumnPattern(String columnPattern) { this.columnPattern = columnPattern; }
    public String getDataTypePattern() { return dataTypePattern; }
    public void setDataTypePattern(String dataTypePattern) { this.dataTypePattern = dataTypePattern; }
    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }
    public String getRuleConfig() { return ruleConfig; }
    public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

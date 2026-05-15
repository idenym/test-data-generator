package com.testdatagen.service;

import com.testdatagen.model.dto.DataGenRequest.FieldRuleRequest;
import com.testdatagen.model.entity.FieldRule;
import com.testdatagen.model.entity.FieldRuleHistory;
import com.testdatagen.model.entity.RuleSet;
import com.testdatagen.model.enums.RuleType;
import com.testdatagen.repository.FieldRuleHistoryRepository;
import com.testdatagen.repository.FieldRuleRepository;
import com.testdatagen.repository.RuleSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final FieldRuleRepository fieldRuleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final FieldRuleHistoryRepository fieldRuleHistoryRepository;

    public RuleService(FieldRuleRepository fieldRuleRepository,
                       RuleSetRepository ruleSetRepository,
                       FieldRuleHistoryRepository fieldRuleHistoryRepository) {
        this.fieldRuleRepository = fieldRuleRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.fieldRuleHistoryRepository = fieldRuleHistoryRepository;
    }

    public List<FieldRule> listAllRules() {
        return fieldRuleRepository.findAllByOrderByPriorityDesc();
    }

    public FieldRule saveRule(FieldRule rule) {
        return fieldRuleRepository.save(rule);
    }

    public FieldRule updateRule(Long id, FieldRule rule) {
        FieldRule existing = fieldRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
        existing.setTablePattern(rule.getTablePattern());
        existing.setColumnPattern(rule.getColumnPattern());
        existing.setDataTypePattern(rule.getDataTypePattern());
        existing.setRuleType(rule.getRuleType());
        existing.setRuleConfig(rule.getRuleConfig());
        existing.setPriority(rule.getPriority());
        existing.setDescription(rule.getDescription());
        existing.setRuleSetId(rule.getRuleSetId());
        return fieldRuleRepository.save(existing);
    }

    public void deleteRule(Long id) {
        fieldRuleRepository.deleteById(id);
    }

    public List<RuleSet> listRuleSets() {
        return ruleSetRepository.findAll();
    }

    public RuleSet saveRuleSet(RuleSet ruleSet) {
        return ruleSetRepository.save(ruleSet);
    }

    public void deleteRuleSet(Long id) {
        ruleSetRepository.deleteById(id);
    }

    /**
     * 保存字段级别的历史规则（带脚本ID绑定）。
     * 如果同一脚本+表字段+ruleType+ruleConfig已存在，更新使用次数和时间；否则新建。
     */
    public void saveFieldRuleHistory(List<FieldRuleRequest> fieldRules, Long sqlScriptId) {
        if (fieldRules == null || fieldRules.isEmpty()) {
            return;
        }

        for (FieldRuleRequest rule : fieldRules) {
            if (rule.getTableName() == null || rule.getColumnName() == null || rule.getRuleType() == null) {
                continue;
            }

            try {
                String ruleConfig = rule.getRuleConfig() != null ? rule.getRuleConfig() : "";
                Optional<FieldRuleHistory> existing;

                if (sqlScriptId != null) {
                    existing = fieldRuleHistoryRepository
                            .findBySqlScriptIdAndTableNameAndColumnNameAndRuleTypeAndRuleConfig(
                                    sqlScriptId, rule.getTableName(), rule.getColumnName(),
                                    rule.getRuleType(), ruleConfig);
                } else {
                    existing = fieldRuleHistoryRepository
                            .findByTableNameAndColumnNameAndRuleTypeAndRuleConfig(
                                    rule.getTableName(), rule.getColumnName(),
                                    rule.getRuleType(), ruleConfig);
                }

                if (existing.isPresent()) {
                    // 已有相同规则，更新使用次数和时间
                    FieldRuleHistory history = existing.get();
                    history.setUsedCount(history.getUsedCount() + 1);
                    history.setLastUsedAt(LocalDateTime.now());
                    // 更新 description（可能用户修改了描述）
                    if (rule.getDescription() != null && !rule.getDescription().isEmpty()) {
                        history.setDescription(rule.getDescription());
                    }
                    fieldRuleHistoryRepository.save(history);
                } else {
                    // 新建历史记录
                    FieldRuleHistory history = new FieldRuleHistory();
                    history.setSqlScriptId(sqlScriptId);
                    history.setTableName(rule.getTableName());
                    history.setColumnName(rule.getColumnName());
                    history.setRuleType(rule.getRuleType());
                    history.setRuleConfig(ruleConfig);
                    history.setDescription(rule.getDescription());
                    fieldRuleHistoryRepository.save(history);
                }
            } catch (Exception e) {
                log.warn("保存字段规则历史失败: {}.{} - {}", rule.getTableName(), rule.getColumnName(), e.getMessage());
            }
        }
    }

    /**
     * 保存字段级别的历史规则（向后兼容，无脚本ID）。
     */
    public void saveFieldRuleHistory(List<FieldRuleRequest> fieldRules) {
        saveFieldRuleHistory(fieldRules, null);
    }

    /**
     * 查询某个表字段的历史规则列表，按最近使用时间倒序排列
     */
    public List<FieldRuleHistory> getFieldRuleHistory(String tableName, String columnName) {
        return fieldRuleHistoryRepository.findByTableNameAndColumnNameOrderByLastUsedAtDesc(tableName, columnName);
    }

    /**
     * 查询指定脚本下某个表字段的历史规则列表
     */
    public List<FieldRuleHistory> getFieldRuleHistory(Long sqlScriptId, String tableName, String columnName) {
        if (sqlScriptId != null) {
            return fieldRuleHistoryRepository.findBySqlScriptIdAndTableNameAndColumnNameOrderByLastUsedAtDesc(
                    sqlScriptId, tableName, columnName);
        }
        return getFieldRuleHistory(tableName, columnName);
    }

    /**
     * 批量获取每个表各字段的最近一次使用的历史规则（用于自动回填）。
     * 返回 Map<tableName, Map<columnName, FieldRuleHistory>>
     */
    public Map<String, Map<String, FieldRuleHistory>> getLatestHistoryRules(
            Long sqlScriptId, Map<String, List<String>> tableColumns) {
        Map<String, Map<String, FieldRuleHistory>> result = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
            String tableName = entry.getKey();
            Map<String, FieldRuleHistory> columnRules = new LinkedHashMap<>();

            for (String columnName : entry.getValue()) {
                try {
                    Optional<FieldRuleHistory> latest;
                    if (sqlScriptId != null) {
                        latest = fieldRuleHistoryRepository
                                .findFirstBySqlScriptIdAndTableNameAndColumnNameOrderByLastUsedAtDesc(
                                        sqlScriptId, tableName, columnName);
                    } else {
                        latest = fieldRuleHistoryRepository
                                .findFirstByTableNameAndColumnNameOrderByLastUsedAtDesc(
                                        tableName, columnName);
                    }
                    latest.ifPresent(h -> columnRules.put(columnName, h));
                } catch (Exception e) {
                    log.warn("查询历史规则失败: {}.{} - {}", tableName, columnName, e.getMessage());
                }
            }

            if (!columnRules.isEmpty()) {
                result.put(tableName, columnRules);
            }
        }
        return result;
    }
}

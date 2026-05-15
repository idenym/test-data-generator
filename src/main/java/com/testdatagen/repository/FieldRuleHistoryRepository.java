package com.testdatagen.repository;

import com.testdatagen.model.entity.FieldRuleHistory;
import com.testdatagen.model.enums.RuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldRuleHistoryRepository extends JpaRepository<FieldRuleHistory, Long> {

    List<FieldRuleHistory> findByTableNameAndColumnNameOrderByLastUsedAtDesc(String tableName, String columnName);

    Optional<FieldRuleHistory> findByTableNameAndColumnNameAndRuleTypeAndRuleConfig(
            String tableName, String columnName, RuleType ruleType, String ruleConfig);

    // 按脚本ID + 表名 + 列名查询历史规则
    List<FieldRuleHistory> findBySqlScriptIdAndTableNameAndColumnNameOrderByLastUsedAtDesc(
            Long sqlScriptId, String tableName, String columnName);

    // 按脚本ID + 表名 + 列名 + 规则类型 + 规则配置判断唯一性
    Optional<FieldRuleHistory> findBySqlScriptIdAndTableNameAndColumnNameAndRuleTypeAndRuleConfig(
            Long sqlScriptId, String tableName, String columnName, RuleType ruleType, String ruleConfig);

    // 查指定表+列的最近一条历史规则（用于自动回填）
    Optional<FieldRuleHistory> findFirstByTableNameAndColumnNameOrderByLastUsedAtDesc(
            String tableName, String columnName);

    // 查指定脚本+表+列的最近一条历史规则（用于自动回填）
    Optional<FieldRuleHistory> findFirstBySqlScriptIdAndTableNameAndColumnNameOrderByLastUsedAtDesc(
            Long sqlScriptId, String tableName, String columnName);
}

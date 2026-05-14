package com.testdatagen.service;

import com.testdatagen.engine.FieldGenerator;
import com.testdatagen.engine.GeneratorFactory;
import com.testdatagen.engine.impl.DefaultGenerator;
import com.testdatagen.engine.impl.ForeignKeyGenerator;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.DataGenRequest.FieldRuleRequest;
import com.testdatagen.model.entity.FieldRule;
import com.testdatagen.model.enums.RuleType;
import com.testdatagen.repository.FieldRuleRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class RuleMatchingEngine {

    private final FieldRuleRepository fieldRuleRepository;

    public RuleMatchingEngine(FieldRuleRepository fieldRuleRepository) {
        this.fieldRuleRepository = fieldRuleRepository;
    }

    /**
     * Match rules for all columns of a table.
     * Returns a map of columnName -> FieldGenerator.
     */
    public Map<String, FieldGenerator> matchRules(String tableName, List<ColumnMetadata> columns,
                                                   List<FieldRuleRequest> userRules, Long ruleSetId) {
        Map<String, FieldGenerator> generators = new LinkedHashMap<>();
        List<FieldRule> storedRules = loadStoredRules(ruleSetId);

        for (ColumnMetadata col : columns) {
            // Skip auto-increment columns
            if (col.isAutoIncrement()) {
                continue;
            }

            FieldGenerator generator = null;

            // 1. Check user-provided rules (highest priority)
            if (userRules != null) {
                generator = matchUserRule(tableName, col, userRules);
            }

            // 2. Check stored rules
            if (generator == null) {
                generator = matchStoredRule(tableName, col, storedRules);
            }

            // 3. Check FK reference
            if (generator == null && col.getReferencedTable() != null) {
                generator = new ForeignKeyGenerator(Collections.emptyList());
            }

            // 4. Built-in heuristic rules
            if (generator == null) {
                generator = matchHeuristicRule(col);
            }

            // 5. Default generator based on data type
            if (generator == null) {
                generator = new DefaultGenerator(col.getDataType(), col.getMaxLength(), col.isNullable());
            }

            generators.put(col.getColumnName(), generator);
        }

        return generators;
    }

    private FieldGenerator matchUserRule(String tableName, ColumnMetadata col, List<FieldRuleRequest> userRules) {
        for (FieldRuleRequest rule : userRules) {
            if (tableName.equalsIgnoreCase(rule.getTableName()) &&
                col.getColumnName().equalsIgnoreCase(rule.getColumnName())) {
                return GeneratorFactory.create(rule.getRuleType(), rule.getRuleConfig(),
                        col.getDataType(), col.getMaxLength(), col.isNullable());
            }
        }
        return null;
    }

    private FieldGenerator matchStoredRule(String tableName, ColumnMetadata col, List<FieldRule> storedRules) {
        for (FieldRule rule : storedRules) {
            boolean tableMatch = matchesPattern(tableName, rule.getTablePattern());
            boolean columnMatch = matchesPattern(col.getColumnName(), rule.getColumnPattern());
            boolean typeMatch = rule.getDataTypePattern() == null || rule.getDataTypePattern().isEmpty() ||
                    matchesPattern(col.getDataType(), rule.getDataTypePattern());

            if (tableMatch && columnMatch && typeMatch) {
                return GeneratorFactory.create(rule.getRuleType(), rule.getRuleConfig(),
                        col.getDataType(), col.getMaxLength(), col.isNullable());
            }
        }
        return null;
    }

    private FieldGenerator matchHeuristicRule(ColumnMetadata col) {
        String name = col.getColumnName().toLowerCase();
        String type = col.getColumnType() != null ? col.getColumnType().toLowerCase() : "";

        // Email pattern
        if (name.contains("email") || name.contains("mail")) {
            return GeneratorFactory.create(RuleType.REGEX, "{\"pattern\":\"[a-z]{5,8}@(gmail|qq|163|outlook)\\\\.com\"}", col.getDataType(), col.getMaxLength(), col.isNullable());
        }

        // Phone pattern
        if (name.contains("phone") || name.contains("mobile") || name.contains("tel")) {
            return GeneratorFactory.create(RuleType.REGEX, "{\"pattern\":\"1[3-9][0-9]{9}\"}", col.getDataType(), col.getMaxLength(), col.isNullable());
        }

        // Status / type fields
        if (name.equals("status")) {
            return GeneratorFactory.create(RuleType.ENUM, "{\"values\":[\"0\",\"1\"],\"weights\":[0.3,0.7]}", col.getDataType(), col.getMaxLength(), col.isNullable());
        }

        // Gender
        if (name.equals("gender") || name.equals("sex")) {
            return GeneratorFactory.create(RuleType.ENUM, "{\"values\":[\"M\",\"F\"],\"weights\":[0.5,0.5]}", col.getDataType(), col.getMaxLength(), col.isNullable());
        }

        // MySQL ENUM type
        if (type.startsWith("enum(")) {
            List<String> enumValues = extractEnumValues(type);
            if (!enumValues.isEmpty()) {
                StringBuilder configBuilder = new StringBuilder("{\"values\":[");
                for (int i = 0; i < enumValues.size(); i++) {
                    if (i > 0) configBuilder.append(",");
                    configBuilder.append("\"").append(enumValues.get(i)).append("\"");
                }
                configBuilder.append("]}");
                return GeneratorFactory.create(RuleType.ENUM, configBuilder.toString(), col.getDataType(), col.getMaxLength(), col.isNullable());
            }
        }

        // Timestamp fields
        if (name.contains("created") || name.contains("updated") || name.contains("create_time") || name.contains("update_time")) {
            return GeneratorFactory.create(RuleType.RANGE,
                    "{\"type\":\"datetime\",\"min\":\"2024-01-01T00:00:00\",\"max\":\"2025-12-31T23:59:59\"}",
                    col.getDataType(), col.getMaxLength(), col.isNullable());
        }

        return null;
    }

    private List<FieldRule> loadStoredRules(Long ruleSetId) {
        if (ruleSetId != null) {
            List<FieldRule> rules = new ArrayList<>();
            rules.addAll(fieldRuleRepository.findByRuleSetIdOrderByPriorityDesc(ruleSetId));
            rules.addAll(fieldRuleRepository.findByRuleSetIdIsNullOrderByPriorityDesc());
            return rules;
        }
        return fieldRuleRepository.findAllByOrderByPriorityDesc();
    }

    private boolean matchesPattern(String value, String pattern) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }
        // Convert glob pattern to regex
        String regex = globToRegex(pattern);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*': regex.append(".*"); break;
                case '?': regex.append("."); break;
                case '.': regex.append("\\."); break;
                default: regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }

    private List<String> extractEnumValues(String enumType) {
        List<String> values = new ArrayList<>();
        int start = enumType.indexOf('(');
        int end = enumType.lastIndexOf(')');
        if (start >= 0 && end > start) {
            String inner = enumType.substring(start + 1, end);
            for (String val : inner.split(",")) {
                values.add(val.trim().replace("'", ""));
            }
        }
        return values;
    }
}

package com.testdatagen.controller;

import com.testdatagen.model.dto.AutoFillRequest;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.model.entity.FieldRule;
import com.testdatagen.model.entity.FieldRuleHistory;
import com.testdatagen.model.entity.RuleSet;
import com.testdatagen.service.KnowledgeBaseService;
import com.testdatagen.service.LlmService;
import com.testdatagen.service.MetadataService;
import com.testdatagen.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;
    private final LlmService llmService;
    private final MetadataService metadataService;
    private final KnowledgeBaseService knowledgeBaseService;

    public RuleController(RuleService ruleService, LlmService llmService,
                          MetadataService metadataService, KnowledgeBaseService knowledgeBaseService) {
        this.ruleService = ruleService;
        this.llmService = llmService;
        this.metadataService = metadataService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public List<FieldRule> list() {
        return ruleService.listAllRules();
    }

    @PostMapping
    public FieldRule create(@RequestBody FieldRule rule) {
        return ruleService.saveRule(rule);
    }

    @PutMapping("/{id}")
    public FieldRule update(@PathVariable Long id, @RequestBody FieldRule rule) {
        return ruleService.updateRule(id, rule);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ruleService.deleteRule(id);
    }

    @GetMapping("/sets")
    public List<RuleSet> listSets() {
        return ruleService.listRuleSets();
    }

    @PostMapping("/sets")
    public RuleSet createSet(@RequestBody RuleSet ruleSet) {
        return ruleService.saveRuleSet(ruleSet);
    }

    @DeleteMapping("/sets/{id}")
    public void deleteSet(@PathVariable Long id) {
        ruleService.deleteRuleSet(id);
    }

    @PostMapping("/suggest/{connectionId}/{tableName}")
    public List<Map<String, Object>> suggest(@PathVariable Long connectionId, @PathVariable String tableName) {
        TableMetadata metadata = metadataService.getTableMetadata(connectionId, tableName);
        return llmService.suggestRules(metadata);
    }

    /**
     * 查询某个表字段的历史规则列表
     */
    @GetMapping("/history")
    public List<FieldRuleHistory> getFieldHistory(@RequestParam String tableName,
                                                   @RequestParam String columnName,
                                                   @RequestParam(required = false) Long sqlScriptId) {
        return ruleService.getFieldRuleHistory(sqlScriptId, tableName, columnName);
    }

    /**
     * 自动回填规则：批量返回历史规则 + 知识库规则，按优先级合并。
     * 前端在此基础上再叠加 WHERE 规则（优先级最高）和 comment 默认值（优先级最低）。
     */
    @PostMapping("/auto-fill")
    public Map<String, List<Map<String, Object>>> autoFill(@RequestBody AutoFillRequest request) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        if (request.getTables() == null || request.getTables().isEmpty()) {
            return result;
        }

        // 1. 收集所有表的列名，用于批量查询历史规则
        Map<String, List<String>> tableColumns = new LinkedHashMap<>();
        for (AutoFillRequest.TableInfo tableInfo : request.getTables()) {
            List<String> colNames = new ArrayList<>();
            if (tableInfo.getColumns() != null) {
                for (AutoFillRequest.ColumnInfo col : tableInfo.getColumns()) {
                    colNames.add(col.getColumnName());
                }
            }
            tableColumns.put(tableInfo.getTableName(), colNames);
        }

        // 2. 批量查询历史规则
        Map<String, Map<String, FieldRuleHistory>> historyRules =
                ruleService.getLatestHistoryRules(request.getSqlScriptId(), tableColumns);

        // 3. 对每个表合并规则（低优先级先放，高优先级后覆盖）
        for (AutoFillRequest.TableInfo tableInfo : request.getTables()) {
            String tableName = tableInfo.getTableName();
            // key=columnName, value=规则信息
            Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

            // 3a. 知识库规则（优先级3）
            if (knowledgeBaseService.isEnabled() && tableInfo.getColumns() != null) {
                List<ColumnMetadata> colMetas = new ArrayList<>();
                for (AutoFillRequest.ColumnInfo ci : tableInfo.getColumns()) {
                    ColumnMetadata cm = new ColumnMetadata();
                    cm.setColumnName(ci.getColumnName());
                    cm.setComment(ci.getComment());
                    cm.setDataType(ci.getDataType());
                    colMetas.add(cm);
                }

                List<Map<String, Object>> kbRules = knowledgeBaseService.queryRules(tableName, colMetas);
                for (Map<String, Object> kbRule : kbRules) {
                    String colName = (String) kbRule.get("columnName");
                    if (colName != null) {
                        Map<String, Object> entry = new LinkedHashMap<>(kbRule);
                        entry.put("source", "KNOWLEDGE_BASE");
                        merged.put(colName, entry);
                    }
                }
            }

            // 3b. 历史规则覆盖（优先级2）
            Map<String, FieldRuleHistory> tableHistory = historyRules.get(tableName);
            if (tableHistory != null) {
                for (Map.Entry<String, FieldRuleHistory> he : tableHistory.entrySet()) {
                    FieldRuleHistory h = he.getValue();
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("columnName", h.getColumnName());
                    entry.put("ruleType", h.getRuleType().name());
                    entry.put("ruleConfig", h.getRuleConfig());
                    entry.put("source", "HISTORY");
                    merged.put(he.getKey(), entry);
                }
            }

            result.put(tableName, new ArrayList<>(merged.values()));
        }

        return result;
    }
}

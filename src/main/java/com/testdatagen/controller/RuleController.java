package com.testdatagen.controller;

import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.model.entity.FieldRule;
import com.testdatagen.model.entity.FieldRuleHistory;
import com.testdatagen.model.entity.RuleSet;
import com.testdatagen.service.LlmService;
import com.testdatagen.service.MetadataService;
import com.testdatagen.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;
    private final LlmService llmService;
    private final MetadataService metadataService;

    public RuleController(RuleService ruleService, LlmService llmService, MetadataService metadataService) {
        this.ruleService = ruleService;
        this.llmService = llmService;
        this.metadataService = metadataService;
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
}

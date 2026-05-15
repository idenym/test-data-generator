package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.testdatagen.config.KnowledgeBaseConfig.KnowledgeBaseProperties;
import com.testdatagen.model.dto.ColumnMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final RestTemplate knowledgeBaseRestTemplate;
    private final KnowledgeBaseProperties properties;

    public KnowledgeBaseService(RestTemplate knowledgeBaseRestTemplate, KnowledgeBaseProperties properties) {
        this.knowledgeBaseRestTemplate = knowledgeBaseRestTemplate;
        this.properties = properties;
    }

    /**
     * 批量查询知识库，传入表名+字段列表(含comment)，返回规则建议。
     * 当知识库未启用或调用失败时返回空列表，不影响主流程。
     *
     * @param tableName 表名
     * @param columns   字段元数据列表（使用 columnName, comment, dataType）
     * @return 规则建议列表，格式与 LLM suggestRules 一致: [{columnName, ruleType, ruleConfig}]
     */
    public List<Map<String, Object>> queryRules(String tableName, List<ColumnMetadata> columns) {
        if (!properties.isEnabled()) {
            return Collections.emptyList();
        }

        try {
            String url = properties.getBaseUrl().replaceAll("/+$", "") + "/api/v1/rules/query";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + properties.getApiKey());
            }

            // 构建请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("tableName", tableName);

            List<Map<String, String>> columnInfos = new ArrayList<>();
            for (ColumnMetadata col : columns) {
                if (col.isAutoIncrement() || col.getReferencedTable() != null) {
                    continue;
                }
                Map<String, String> colInfo = new LinkedHashMap<>();
                colInfo.put("columnName", col.getColumnName());
                colInfo.put("comment", col.getComment() != null ? col.getComment() : "");
                colInfo.put("dataType", col.getDataType() != null ? col.getDataType() : "");
                columnInfos.add(colInfo);
            }
            requestBody.put("columns", columnInfos);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), headers);
            ResponseEntity<String> response = knowledgeBaseRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> result = JSON.parseObject(
                        response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
                log.info("知识库查询成功: 表={}, 返回 {} 条规则", tableName, result != null ? result.size() : 0);
                return result != null ? result : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("知识库查询失败 (表={}): {}", tableName, e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * 判断知识库是否已启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}

package com.testdatagen.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.testdatagen.config.OpenAiConfig.OpenAiProperties;
import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final RestTemplate openAiRestTemplate;
    private final OpenAiProperties openAiProperties;

    public LlmService(RestTemplate openAiRestTemplate, OpenAiProperties openAiProperties) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.openAiProperties = openAiProperties;
    }

    public List<Object> generateBatchValues(String tableName, ColumnMetadata column,
                                            String description, int batchSize, List<String> models) {
        String prompt = buildGenerationPrompt(tableName, column, description, batchSize);
        String model = pickModel(models);
        String response = callOpenAi(prompt, model);
        return parseValuesFromResponse(response, batchSize);
    }

    public List<Map<String, Object>> suggestRules(TableMetadata tableMetadata) {
        String prompt = buildSuggestionPrompt(tableMetadata);
        String response = callOpenAi(prompt, null);
        try {
            return JSON.parseObject(response, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("解析LLM规则建议失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从用户选择的模型列表中随机挑选一个模型
     * 如果列表为空或null，使用配置文件中的默认模型
     */
    private String pickModel(List<String> models) {
        if (models == null || models.isEmpty()) {
            return openAiProperties.getModel();
        }
        if (models.size() == 1) {
            return models.get(0);
        }
        return models.get(ThreadLocalRandom.current().nextInt(models.size()));
    }

    private String buildGenerationPrompt(String tableName, ColumnMetadata column, String description, int batchSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(batchSize).append(" realistic test data values for a database column.\n\n");
        sb.append("Table: ").append(tableName).append("\n");
        sb.append("Column: ").append(column.getColumnName()).append("\n");
        sb.append("Data type: ").append(column.getColumnType()).append("\n");
        if (column.getMaxLength() != null) {
            sb.append("Max length: ").append(column.getMaxLength()).append("\n");
        }
        sb.append("Nullable: ").append(column.isNullable()).append("\n");
        if (column.getComment() != null && !column.getComment().isEmpty()) {
            sb.append("Column comment: ").append(column.getComment()).append("\n");
        }
        sb.append("Description: ").append(description).append("\n\n");
        sb.append("要求:\n");
        sb.append("- 生成的数值需要 realistic 和 diverse，生成数据可以不唯一，尽量满足多元\n");
        sb.append("- 数据生成的规则，需要严格满足Data type所代表的数据类型和对应数据类型的最大限制以及Nullable代表的是否可空的限制，然后根据Description来构造数据，如果Description可以根据Table和Column的语义构造\n");
        sb.append("- 只返回生成数据，以一个json数组格式，不要返回思考过程等其他内容\n");
        sb.append("- 注意区分bigint和bigint unsigned的数值边界");
        sb.append("- 返回示例: [\"value1\", \"value2\", ...]\n");
        return sb.toString();
    }

    private String buildSuggestionPrompt(TableMetadata tableMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze this database table and suggest data generation rules for each column.\n\n");
        sb.append("Table: ").append(tableMetadata.getTableName()).append("\n");
        if (tableMetadata.getTableComment() != null) {
            sb.append("Comment: ").append(tableMetadata.getTableComment()).append("\n");
        }
        sb.append("\nColumns:\n");
        for (ColumnMetadata col : tableMetadata.getColumns()) {
            sb.append("- ").append(col.getColumnName())
                    .append(" (").append(col.getColumnType()).append(")");
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                sb.append(" -- ").append(col.getComment());
            }
            if (col.isAutoIncrement()) sb.append(" [AUTO_INCREMENT]");
            if (col.isPrimaryKey()) sb.append(" [PK]");
            if (col.getReferencedTable() != null) sb.append(" [FK->").append(col.getReferencedTable()).append("]");
            sb.append("\n");
        }
        sb.append("\nFor each non-auto-increment column, suggest the best approach to generate realistic test data.\n");
        sb.append("Return JSON array: [{\"columnName\":\"...\",\"ruleType\":\"REGEX|RANGE|ENUM|LLM_DESCRIPTION\",\"ruleConfig\":{...},\"description\":\"...\"}]\n");
        sb.append("ruleType options: REGEX (with pattern), RANGE (with min/max/type), ENUM (with values/weights), LLM_DESCRIPTION (with description)\n");
        sb.append("\n请用中文回答，description字段请使用中文描述。\n");
        return sb.toString();
    }

    private String callOpenAi(String userPrompt, String model) {
        // 使用传入的模型，如果为空则用默认配置
        String actualModel = (model != null && !model.isEmpty()) ? model : openAiProperties.getModel();

        // 根据模型ID获取对应的配置（baseUrl、apiKey及模型级参数）
        com.testdatagen.config.OpenAiConfig.ModelConfig modelConfig = openAiProperties.getModelConfig(actualModel);
        String baseUrl = modelConfig.getBaseUrl() != null ? modelConfig.getBaseUrl() : openAiProperties.getBaseUrl();
        String apiKey = modelConfig.getApiKey() != null ? modelConfig.getApiKey() : openAiProperties.getApiKey();
        int maxTokens = modelConfig.getMaxTokens() != null ? modelConfig.getMaxTokens() : openAiProperties.getMaxTokens();
        double temperature = modelConfig.getTemperature() != null ? modelConfig.getTemperature() : openAiProperties.getTemperature();
        boolean enableThinking = modelConfig.getEnableThinking() != null ? modelConfig.getEnableThinking() : openAiProperties.isEnableThinking();

        String url = baseUrl.replaceAll("/+$", "") + "/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        JSONObject body = new JSONObject();
        body.put("model", actualModel);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if(!actualModel.startsWith("deepseek")){
            body.put("thinking", enableThinking);
        }

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are a test data generator. Generate realistic test data values. Return ONLY valid JSON, no explanations or markdown formatting.");
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        body.put("messages", messages);

        log.info("LLM请求 - 模型: {}, URL: {}, 完整messages内容: {}", actualModel, url, messages.toJSONString());

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
                ResponseEntity<String> response = openAiRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JSONObject responseJson = JSON.parseObject(response.getBody());
                    JSONArray choices = responseJson.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String content = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        // Strip markdown code blocks if present
                        content = content.replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "").trim();
                        log.info("大模型结果输出：模型: {}，输入 userPrompt：{}，", actualModel, userPrompt);
                        log.info("--------------------------------");
                        log.info("大模型原始出参:{}", responseJson);
                        log.info("--------------------------------");
                        log.info("大模型造数结果:{}", content);
                        return content;
                    }
                }
            } catch (Exception e) {
                log.warn("OpenAI API调用失败 (尝试 {}/{}): {}", attempt + 1, maxRetries, e.getMessage(), e);
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return "[]";
    }

    private List<Object> parseValuesFromResponse(String response, int expectedSize) {
        List<Object> values = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(response);
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    Object item = array.get(i);
                    if (item instanceof Number) {
                        values.add(item);
                    } else {
                        values.add(String.valueOf(item));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析LLM响应失败: {}", e.getMessage());
        }
        return values;
    }
}

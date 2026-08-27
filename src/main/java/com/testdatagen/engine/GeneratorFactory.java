package com.testdatagen.engine;

import com.testdatagen.engine.impl.*;
import com.testdatagen.model.enums.RuleType;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GeneratorFactory {

    public static FieldGenerator create(RuleType ruleType, String ruleConfigJson, String dataType, Integer maxLength, boolean nullable, Integer numericScale) {
        try {
            if (ruleType == null) {
                return new DefaultGenerator(dataType, maxLength, nullable, numericScale);
            }

            JSONObject config = (ruleConfigJson != null && !ruleConfigJson.isEmpty())
                    ? JSON.parseObject(ruleConfigJson) : new JSONObject();

            switch (ruleType) {
                case REGEX:
                    String pattern = config.getString("pattern");
                    return new RegexGenerator(pattern != null ? pattern : "\\w{10}");

                case RANGE:
                    String type = config.getString("type");
                    String min = config.getString("min");
                    String max = config.getString("max");
                    return new RangeGenerator(
                            type != null ? type : "integer",
                            min != null ? min : "0",
                            max != null ? max : "100");

                case ENUM:
                    List<String> values = new ArrayList<>();
                    List<Double> weights = new ArrayList<>();
                    JSONArray valuesNode = config.getJSONArray("values");
                    if (valuesNode != null) {
                        for (int i = 0; i < valuesNode.size(); i++) {
                            values.add(valuesNode.getString(i));
                        }
                    }
                    JSONArray weightsNode = config.getJSONArray("weights");
                    if (weightsNode != null) {
                        for (int i = 0; i < weightsNode.size(); i++) {
                            weights.add(weightsNode.getDoubleValue(i));
                        }
                    }
                    if (values.isEmpty()) {
                        return new DefaultGenerator(dataType, maxLength, nullable, numericScale);
                    }
                    return new EnumGenerator(values, weights);

                case LLM_DESCRIPTION:
                    LlmBatchGenerator llmGen = new LlmBatchGenerator();
                    String desc = config.getString("description");
                    if (desc != null && !desc.isEmpty()) {
                        llmGen.setDescription(desc);
                    }
                    return llmGen;

                default:
                    return new DefaultGenerator(dataType, maxLength, nullable, numericScale);
            }
        } catch (Exception e) {
            return new DefaultGenerator(dataType, maxLength, nullable, numericScale);
        }
    }
}

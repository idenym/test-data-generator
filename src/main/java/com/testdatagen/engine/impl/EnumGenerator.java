package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnumGenerator implements FieldGenerator {

    private final List<String> values;
    private final List<Double> weights;

    public EnumGenerator(List<String> values, List<Double> weights) {
        this.values = values;
        this.weights = weights;
    }

    @Override
    public Object generate(Map<String, Object> context) {
        if (weights != null && !weights.isEmpty() && weights.size() == values.size()) {
            double r = ThreadLocalRandom.current().nextDouble();
            double cumulative = 0;
            for (int i = 0; i < values.size(); i++) {
                cumulative += weights.get(i);
                if (r <= cumulative) {
                    return values.get(i);
                }
            }
            return values.get(values.size() - 1);
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}

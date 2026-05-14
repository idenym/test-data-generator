package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class LlmBatchGenerator implements FieldGenerator {

    private final CopyOnWriteArrayList<Object> valuePool = new CopyOnWriteArrayList<>();
    private final AtomicInteger index = new AtomicInteger(0);
    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void addValues(List<Object> values) {
        valuePool.addAll(values);
    }

    public boolean needsMoreValues(int upcoming) {
        return index.get() + upcoming > valuePool.size();
    }

    public int getPoolSize() {
        return valuePool.size();
    }

    @Override
    public Object generate(Map<String, Object> context) {
        if (valuePool.isEmpty()) {
            return "LLM_PLACEHOLDER";
        }
        int i = index.getAndIncrement();
        if (i < valuePool.size()) {
            return valuePool.get(i);
        }
        // Cycle if pool exhausted
        return valuePool.get(ThreadLocalRandom.current().nextInt(valuePool.size()));
    }
}

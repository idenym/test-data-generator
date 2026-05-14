package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ForeignKeyGenerator implements FieldGenerator {

    private List<Object> parentKeys;

    public ForeignKeyGenerator(List<Object> parentKeys) {
        this.parentKeys = parentKeys;
    }

    public void setParentKeys(List<Object> parentKeys) {
        this.parentKeys = parentKeys;
    }

    @Override
    public Object generate(Map<String, Object> context) {
        if (parentKeys == null || parentKeys.isEmpty()) {
            return null;
        }
        return parentKeys.get(ThreadLocalRandom.current().nextInt(parentKeys.size()));
    }
}

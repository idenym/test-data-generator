package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SequenceGenerator implements FieldGenerator {

    private final AtomicLong counter;

    public SequenceGenerator(long start) {
        this.counter = new AtomicLong(start);
    }

    @Override
    public Object generate(Map<String, Object> context) {
        return counter.getAndIncrement();
    }
}

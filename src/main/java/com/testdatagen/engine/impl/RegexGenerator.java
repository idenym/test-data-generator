package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;
import com.github.curiousoddman.rgxgen.RgxGen;

import java.util.Map;

public class RegexGenerator implements FieldGenerator {

    private final RgxGen rgxGen;

    public RegexGenerator(String pattern) {
        this.rgxGen = RgxGen.parse(pattern);
    }

    @Override
    public Object generate(Map<String, Object> context) {
        return rgxGen.generate();
    }
}

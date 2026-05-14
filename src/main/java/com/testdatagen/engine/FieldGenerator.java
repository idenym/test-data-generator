package com.testdatagen.engine;

import java.util.Map;

public interface FieldGenerator {

    /**
     * Generate a single value for a field.
     * @param context generation context containing row index, other column values, FK pools, etc.
     * @return generated value
     */
    Object generate(Map<String, Object> context);
}

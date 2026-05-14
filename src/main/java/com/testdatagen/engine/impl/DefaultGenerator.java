package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;
import com.testdatagen.util.SqlTypeMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DefaultGenerator implements FieldGenerator {

    private final String dataType;
    private final Integer maxLength;
    private final boolean nullable;

    public DefaultGenerator(String dataType, Integer maxLength, boolean nullable) {
        this.dataType = dataType;
        this.maxLength = maxLength;
        this.nullable = nullable;
    }

    @Override
    public Object generate(Map<String, Object> context) {
        if (dataType == null) {
            return "test_" + ThreadLocalRandom.current().nextInt(100000);
        }

        String javaType = SqlTypeMapper.toJavaType(dataType);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        switch (javaType) {
            case "Integer":
                return random.nextInt(1, 10000);
            case "Long":
                return random.nextLong(1, 1000000L);
            case "Float":
                return (float) (random.nextDouble() * 1000);
            case "Double":
                return random.nextDouble() * 10000;
            case "BigDecimal":
                return BigDecimal.valueOf(random.nextDouble() * 10000).setScale(2, BigDecimal.ROUND_HALF_UP);
            case "Boolean":
                return random.nextBoolean();
            case "Date":
                return LocalDate.now().minusDays(random.nextInt(365));
            case "DateTime":
                return LocalDateTime.now().minusDays(random.nextInt(365)).minusHours(random.nextInt(24));
            case "Time":
                return String.format("%02d:%02d:%02d", random.nextInt(24), random.nextInt(60), random.nextInt(60));
            case "Bytes":
                return null; // Skip binary columns
            case "String":
            default:
                int len = maxLength != null ? Math.min(maxLength, 20) : 10;
                return generateRandomString(len);
        }
    }

    private String generateRandomString(int maxLen) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(uuid.length(), maxLen));
    }
}

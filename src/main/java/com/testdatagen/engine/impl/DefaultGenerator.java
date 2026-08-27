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
    /** 数值列小数位数（DECIMAL 的 scale）；此时 maxLength 表示总精度 */
    private final Integer numericScale;

    public DefaultGenerator(String dataType, Integer maxLength, boolean nullable) {
        this(dataType, maxLength, nullable, null);
    }

    public DefaultGenerator(String dataType, Integer maxLength, boolean nullable, Integer numericScale) {
        this.dataType = dataType;
        this.maxLength = maxLength;
        this.nullable = nullable;
        this.numericScale = numericScale;
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
                return randomDecimal(random);
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

    /**
     * 生成 DECIMAL 值：已知精度/小数位时严格控制在列范围内，
     * 避免写入时 Out of range（如 DECIMAL(6,4) 最大 99.9999）。
     */
    private BigDecimal randomDecimal(ThreadLocalRandom random) {
        if (maxLength != null && maxLength > 0) {
            int scale = numericScale != null && numericScale >= 0 ? Math.min(numericScale, maxLength) : 2;
            int intDigits = maxLength - scale;
            if (intDigits < 0) {
                intDigits = 0;
                scale = maxLength;
            }
            double max = Math.pow(10, intDigits);
            BigDecimal val = BigDecimal.valueOf(random.nextDouble() * max);
            // ROUND_DOWN 确保不会因进位触顶越界
            return val.setScale(scale, BigDecimal.ROUND_DOWN);
        }
        return BigDecimal.valueOf(random.nextDouble() * 10000).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private String generateRandomString(int maxLen) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(uuid.length(), maxLen));
    }
}

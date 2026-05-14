package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RangeGenerator implements FieldGenerator {

    private final String type;
    private final String min;
    private final String max;

    public RangeGenerator(String type, String min, String max) {
        this.type = type != null ? type : "integer";
        this.min = min;
        this.max = max;
    }

    @Override
    public Object generate(Map<String, Object> context) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (type.toLowerCase()) {
            case "integer":
            case "int":
                return random.nextInt(Integer.parseInt(min), Integer.parseInt(max) + 1);
            case "long":
                return random.nextLong(Long.parseLong(min), Long.parseLong(max) + 1);
            case "double":
            case "float":
            case "decimal":
                double minD = Double.parseDouble(min);
                double maxD = Double.parseDouble(max);
                double val = minD + random.nextDouble() * (maxD - minD);
                return BigDecimal.valueOf(val).setScale(2, BigDecimal.ROUND_HALF_UP);
            case "date":
                LocalDate startDate = LocalDate.parse(min);
                LocalDate endDate = LocalDate.parse(max);
                long days = ChronoUnit.DAYS.between(startDate, endDate);
                return startDate.plusDays(random.nextLong(days + 1));
            case "datetime":
                LocalDateTime startDt = LocalDateTime.parse(min.replace(" ", "T"));
                LocalDateTime endDt = LocalDateTime.parse(max.replace(" ", "T"));
                long seconds = ChronoUnit.SECONDS.between(startDt, endDt);
                return startDt.plusSeconds(random.nextLong(seconds + 1));
            default:
                return random.nextInt(Integer.parseInt(min), Integer.parseInt(max) + 1);
        }
    }
}

package com.testdatagen.engine.impl;

import com.testdatagen.engine.FieldGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                return nextInt(random);
            case "long":
                return nextLong(random);
            case "double":
            case "float":
            case "decimal":
                return nextDecimal(random);
            case "date":
                return nextDate(random);
            case "datetime":
                return nextDateTime(random);
            default:
                return nextInt(random);
        }
    }

    /* ================= 整数类型 ================= */

    private int nextInt(ThreadLocalRandom random) {
        int minVal = parseIntMin();
        int maxVal = parseIntMax();
        if (minVal >= maxVal) {
            return minVal;
        }
        long range = (long) maxVal - (long) minVal + 1L;
        if (range > Integer.MAX_VALUE) {
            return minVal + (int) (random.nextDouble() * range);
        }
        return minVal + random.nextInt((int) range);
    }

    private int parseIntMin() {
        if (isBlank(min)) return 0;
        try { return Integer.parseInt(min.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private int parseIntMax() {
        if (isBlank(max)) return Integer.MAX_VALUE;
        try { return Integer.parseInt(max.trim()); }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }

    /* ================= Long 类型 ================= */

    private long nextLong(ThreadLocalRandom random) {
        long minVal = parseLongMin();
        long maxVal = parseLongMax();
        if (minVal >= maxVal) {
            return minVal;
        }
        java.math.BigDecimal range = java.math.BigDecimal.valueOf(maxVal)
                .subtract(java.math.BigDecimal.valueOf(minVal))
                .add(java.math.BigDecimal.ONE);
        if (range.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return minVal + (long) (random.nextDouble() * range.doubleValue());
        }
        return minVal + random.nextLong(range.longValue());
    }

    private long parseLongMin() {
        if (isBlank(min)) return 0L;
        try { return Long.parseLong(min.trim()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private long parseLongMax() {
        if (isBlank(max)) return Long.MAX_VALUE;
        try { return Long.parseLong(max.trim()); }
        catch (NumberFormatException e) { return Long.MAX_VALUE; }
    }

    /* ================= 浮点类型 ================= */

    private BigDecimal nextDecimal(ThreadLocalRandom random) {
        double minD = parseDoubleMin();
        double maxD = parseDoubleMax();
        if (minD >= maxD) {
            return BigDecimal.valueOf(minD).setScale(2, RoundingMode.HALF_UP);
        }
        double val = minD + random.nextDouble() * (maxD - minD);
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
    }

    private double parseDoubleMin() {
        if (isBlank(min)) return 0.0;
        try { return Double.parseDouble(min.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private double parseDoubleMax() {
        if (isBlank(max)) return 999999.99;
        try { return Double.parseDouble(max.trim()); }
        catch (NumberFormatException e) { return 999999.99; }
    }

    /* ================= 日期类型 ================= */

    private LocalDate nextDate(ThreadLocalRandom random) {
        LocalDate start = parseDateMin();
        LocalDate end = parseDateMax();
        if (!start.isBefore(end)) {
            return start;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        return start.plusDays(random.nextLong(days + 1));
    }

    private LocalDate parseDateMin() {
        if (isBlank(min)) return LocalDate.of(1970, 1, 1);
        try { return LocalDate.parse(min.trim()); }
        catch (Exception e) { return LocalDate.of(1970, 1, 1); }
    }

    private LocalDate parseDateMax() {
        if (isBlank(max)) return LocalDate.of(2099, 12, 31);
        try { return LocalDate.parse(max.trim()); }
        catch (Exception e) { return LocalDate.of(2099, 12, 31); }
    }

    /* ================= 日期时间类型 ================= */

    private LocalDateTime nextDateTime(ThreadLocalRandom random) {
        LocalDateTime start = parseDateTimeMin();
        LocalDateTime end = parseDateTimeMax();
        if (!start.isBefore(end)) {
            return start;
        }
        long seconds = ChronoUnit.SECONDS.between(start, end);
        return start.plusSeconds(random.nextLong(seconds + 1));
    }

    private LocalDateTime parseDateTimeMin() {
        if (isBlank(min)) return LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        try {
            String normalized = min.trim().replace(" ", "T");
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            return LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        }
    }

    private LocalDateTime parseDateTimeMax() {
        if (isBlank(max)) return LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        try {
            String normalized = max.trim().replace(" ", "T");
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            return LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

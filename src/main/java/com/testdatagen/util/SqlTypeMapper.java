package com.testdatagen.util;

import java.util.HashMap;
import java.util.Map;

public class SqlTypeMapper {

    private static final Map<String, String> MYSQL_TO_JAVA = new HashMap<>();

    static {
        MYSQL_TO_JAVA.put("TINYINT", "Integer");
        MYSQL_TO_JAVA.put("SMALLINT", "Integer");
        MYSQL_TO_JAVA.put("MEDIUMINT", "Integer");
        MYSQL_TO_JAVA.put("INT", "Integer");
        MYSQL_TO_JAVA.put("INTEGER", "Integer");
        MYSQL_TO_JAVA.put("BIGINT", "Long");
        MYSQL_TO_JAVA.put("FLOAT", "Float");
        MYSQL_TO_JAVA.put("DOUBLE", "Double");
        MYSQL_TO_JAVA.put("DECIMAL", "BigDecimal");
        MYSQL_TO_JAVA.put("NUMERIC", "BigDecimal");
        MYSQL_TO_JAVA.put("VARCHAR", "String");
        MYSQL_TO_JAVA.put("CHAR", "String");
        MYSQL_TO_JAVA.put("TEXT", "String");
        MYSQL_TO_JAVA.put("TINYTEXT", "String");
        MYSQL_TO_JAVA.put("MEDIUMTEXT", "String");
        MYSQL_TO_JAVA.put("LONGTEXT", "String");
        MYSQL_TO_JAVA.put("DATE", "Date");
        MYSQL_TO_JAVA.put("DATETIME", "DateTime");
        MYSQL_TO_JAVA.put("TIMESTAMP", "DateTime");
        MYSQL_TO_JAVA.put("TIME", "Time");
        MYSQL_TO_JAVA.put("YEAR", "Integer");
        MYSQL_TO_JAVA.put("BOOLEAN", "Boolean");
        MYSQL_TO_JAVA.put("BIT", "Boolean");
        MYSQL_TO_JAVA.put("BLOB", "Bytes");
        MYSQL_TO_JAVA.put("TINYBLOB", "Bytes");
        MYSQL_TO_JAVA.put("MEDIUMBLOB", "Bytes");
        MYSQL_TO_JAVA.put("LONGBLOB", "Bytes");
        MYSQL_TO_JAVA.put("BINARY", "Bytes");
        MYSQL_TO_JAVA.put("VARBINARY", "Bytes");
        MYSQL_TO_JAVA.put("JSON", "String");
        MYSQL_TO_JAVA.put("ENUM", "String");
        MYSQL_TO_JAVA.put("SET", "String");
    }

    public static String toJavaType(String mysqlType) {
        if (mysqlType == null) return "String";
        String upper = mysqlType.toUpperCase().trim();
        // Remove length specifier, e.g., VARCHAR(255) -> VARCHAR
        int parenIdx = upper.indexOf('(');
        if (parenIdx > 0) {
            upper = upper.substring(0, parenIdx);
        }
        upper = upper.trim();
        return MYSQL_TO_JAVA.getOrDefault(upper, "String");
    }

    public static boolean isNumericType(String mysqlType) {
        String javaType = toJavaType(mysqlType);
        return "Integer".equals(javaType) || "Long".equals(javaType) ||
               "Float".equals(javaType) || "Double".equals(javaType) || "BigDecimal".equals(javaType);
    }

    public static boolean isDateType(String mysqlType) {
        String javaType = toJavaType(mysqlType);
        return "Date".equals(javaType) || "DateTime".equals(javaType) || "Time".equals(javaType);
    }

    public static boolean isStringType(String mysqlType) {
        return "String".equals(toJavaType(mysqlType));
    }
}

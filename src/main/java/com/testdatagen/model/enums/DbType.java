package com.testdatagen.model.enums;

/**
 * 支持的数据库类型。
 * 每种类型对应不同的 JDBC 驱动、URL 模式和元数据查询方式。
 */
public enum DbType {

    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql", 3306),
    GAUSSDB("GaussDB", "org.postgresql.Driver", "jdbc:postgresql", 5432),
    TDSQL("TDSQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql", 3306),
    HIVE("Hive", "org.apache.hive.jdbc.HiveDriver", "jdbc:hive2", 10000);

    private final String displayName;
    private final String driverClassName;
    private final String urlPrefix;
    private final int defaultPort;

    DbType(String displayName, String driverClassName, String urlPrefix, int defaultPort) {
        this.displayName = displayName;
        this.driverClassName = driverClassName;
        this.urlPrefix = urlPrefix;
        this.defaultPort = defaultPort;
    }

    public String getDisplayName() { return displayName; }
    public String getDriverClassName() { return driverClassName; }
    public String getUrlPrefix() { return urlPrefix; }
    public int getDefaultPort() { return defaultPort; }

    /**
     * 是否为 PostgreSQL 兼容类型（GaussDB）
     */
    public boolean isPgCompatible() {
        return this == GAUSSDB;
    }

    /**
     * 是否为 MySQL 兼容类型（MySQL, TDSQL）
     */
    public boolean isMySQLCompatible() {
        return this == MYSQL || this == TDSQL;
    }

    /**
     * 是否支持事务（Hive 不支持传统事务）
     */
    public boolean supportsTransaction() {
        return this != HIVE;
    }

    /**
     * 标识符引号风格
     */
    public String getQuoteChar() {
        if (isPgCompatible()) {
            return "\"";
        }
        if (this == HIVE) {
            return "`";
        }
        return "`"; // MySQL, TDSQL
    }
}

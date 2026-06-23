package com.testdatagen.util;

import com.testdatagen.model.entity.ConnectionConfig;
import com.testdatagen.model.enums.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JdbcConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(JdbcConnectionFactory.class);

    private final EncryptionUtil encryptionUtil;
    private final Map<String, Boolean> loadedDrivers = new ConcurrentHashMap<>();

    public JdbcConnectionFactory(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    public Connection createConnection(ConnectionConfig config) throws SQLException {
        String url = buildJdbcUrl(config);
        String password = config.getEncryptedPassword() != null
                ? encryptionUtil.decrypt(config.getEncryptedPassword()) : "";
        loadDriver(config.getDbType());
        return DriverManager.getConnection(url, config.getUsername(), password);
    }

    public Connection createConnection(DbType dbType, String host, int port, String username, String password,
                                        String databaseName, String extraParams) throws SQLException {
        String url = buildJdbcUrl(dbType, host, port, databaseName, extraParams);
        loadDriver(dbType);
        return DriverManager.getConnection(url, username, password);
    }

    public String buildJdbcUrl(ConnectionConfig config) {
        return buildJdbcUrl(config.getDbType(), config.getHost(), config.getPort(),
                config.getDatabaseName(), config.getExtraParams());
    }

    public String buildJdbcUrl(DbType dbType, String host, int port, String databaseName, String extraParams) {
        if (dbType == null) dbType = DbType.MYSQL;

        StringBuilder url = new StringBuilder();

        switch (dbType) {
            case MYSQL:
            case TDSQL:
                // MySQL / TDSQL 共用 jdbc:mysql:// 协议
                url.append("jdbc:mysql://").append(host).append(":").append(port)
                        .append("/").append(databaseName);
                url.append("?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8");
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("&").append(extraParams);
                }
                break;

            case GAUSSDB:
                // GaussDB 兼容 PostgreSQL 协议
                url.append("jdbc:postgresql://").append(host).append(":").append(port)
                        .append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            case HIVE:
                // HiveServer2
                url.append("jdbc:hive2://").append(host).append(":").append(port)
                        .append("/").append(databaseName);
                if (extraParams != null && !extraParams.isEmpty()) {
                    url.append("?").append(extraParams);
                }
                break;

            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }

        return url.toString();
    }

    private void loadDriver(DbType dbType) {
        String driverClass = dbType.getDriverClassName();
        loadedDrivers.computeIfAbsent(driverClass, cls -> {
            try {
                Class.forName(cls);
                log.info("已加载 JDBC 驱动: {}", cls);
                return true;
            } catch (ClassNotFoundException e) {
                log.error("JDBC 驱动加载失败: {} - 请确认 pom.xml 中已添加对应依赖", cls);
                return false;
            }
        });
    }
}


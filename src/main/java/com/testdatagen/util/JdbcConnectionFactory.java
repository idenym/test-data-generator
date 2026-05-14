package com.testdatagen.util;

import com.testdatagen.model.entity.ConnectionConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class JdbcConnectionFactory {

    private final EncryptionUtil encryptionUtil;

    public JdbcConnectionFactory(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    public Connection createConnection(ConnectionConfig config) throws SQLException {
        String url = buildJdbcUrl(config);
        String password = encryptionUtil.decrypt(config.getEncryptedPassword());
        return DriverManager.getConnection(url, config.getUsername(), password);
    }

    public Connection createConnection(String host, int port, String username, String password, String databaseName, String extraParams) throws SQLException {
        String url = buildJdbcUrl(host, port, databaseName, extraParams);
        return DriverManager.getConnection(url, username, password);
    }

    private String buildJdbcUrl(ConnectionConfig config) {
        return buildJdbcUrl(config.getHost(), config.getPort(), config.getDatabaseName(), config.getExtraParams());
    }

    private String buildJdbcUrl(String host, int port, String databaseName, String extraParams) {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(databaseName);
        url.append("?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8");
        if (extraParams != null && !extraParams.isEmpty()) {
            url.append("&").append(extraParams);
        }
        return url.toString();
    }
}

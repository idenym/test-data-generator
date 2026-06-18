package com.testdatagen.service;

import com.testdatagen.model.dto.ConnectionRequest;
import com.testdatagen.model.dto.ConnectionTestResult;
import com.testdatagen.model.entity.ConnectionConfig;
import com.testdatagen.repository.ConnectionConfigRepository;
import com.testdatagen.security.CurrentUserContext;
import com.testdatagen.util.EncryptionUtil;
import com.testdatagen.util.JdbcConnectionFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

@Service
public class ConnectionService {

    private final ConnectionConfigRepository repository;
    private final EncryptionUtil encryptionUtil;
    private final JdbcConnectionFactory connectionFactory;

    public ConnectionService(ConnectionConfigRepository repository,
                             EncryptionUtil encryptionUtil,
                             JdbcConnectionFactory connectionFactory) {
        this.repository = repository;
        this.encryptionUtil = encryptionUtil;
        this.connectionFactory = connectionFactory;
    }

    public List<ConnectionConfig> listAll() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return repository.findAll();
        }
        if (CurrentUserContext.isAdmin()) {
            return repository.findAll();
        }
        return repository.findAllByUserIdOrUserIdIsNull(userId);
    }

    public ConnectionConfig getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("连接配置不存在: " + id));
    }

    public ConnectionConfig save(ConnectionRequest request) {
        ConnectionConfig config = new ConnectionConfig();
        config.setName(request.getName());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername());
        config.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        config.setDatabaseName(request.getDatabaseName());
        config.setExtraParams(request.getExtraParams());
        config.setUserId(CurrentUserContext.getUserId());
        return repository.save(config);
    }

    public ConnectionConfig update(Long id, ConnectionRequest request) {
        ConnectionConfig config = getById(id);
        config.setName(request.getName());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            config.setEncryptedPassword(encryptionUtil.encrypt(request.getPassword()));
        }
        config.setDatabaseName(request.getDatabaseName());
        config.setExtraParams(request.getExtraParams());
        return repository.save(config);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public ConnectionTestResult testConnection(ConnectionRequest request) {
        long start = System.currentTimeMillis();
        try (Connection conn = connectionFactory.createConnection(
                request.getHost(), request.getPort(),
                request.getUsername(), request.getPassword(),
                request.getDatabaseName(), request.getExtraParams())) {
            DatabaseMetaData meta = conn.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            long latency = System.currentTimeMillis() - start;
            return ConnectionTestResult.ok(version, latency);
        } catch (Exception e) {
            return ConnectionTestResult.fail("连接失败: " + e.getMessage());
        }
    }

    public Connection getConnection(Long connectionId) {
        ConnectionConfig config = getById(connectionId);
        try {
            return connectionFactory.createConnection(config);
        } catch (Exception e) {
            throw new RuntimeException("无法连接到数据库: " + e.getMessage(), e);
        }
    }
}

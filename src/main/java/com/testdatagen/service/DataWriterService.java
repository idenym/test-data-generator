package com.testdatagen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataWriterService {

    private static final Logger log = LoggerFactory.getLogger(DataWriterService.class);

    private final ConnectionService connectionService;

    public DataWriterService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /**
     * 在同一个事务中写入多张表的数据。
     * 任意一张表写入失败，所有已写入的数据都会回滚。
     */
    public void writeAllTablesInTransaction(Long connectionId, List<TableWriteTask> tasks, int batchSize) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        try (Connection conn = connectionService.getConnection(connectionId)) {
            conn.setAutoCommit(false);
            try {
                for (TableWriteTask task : tasks) {
                    if (task.getRows().isEmpty() || task.getColumns().isEmpty()) {
                        continue;
                    }
                    writeRowsOnConnection(conn, task.getTableName(), task.getColumns(), task.getRows(), batchSize);
                }
                conn.commit();
                log.info("事务提交成功，共写入 {} 张表", tasks.size());
            } catch (Exception e) {
                conn.rollback();
                log.error("事务回滚，写入失败: {}", e.getMessage());
                throw new RuntimeException("写入数据失败，已回滚所有操作: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取数据库连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用已有连接写入数据（不管理事务），返回自增主键列表。
     * 由调用方负责事务管理（commit/rollback）。
     */
    public List<Object> writeRowsOnConnection(Connection conn, String tableName, List<String> columns,
                                               List<Map<String, Object>> rows, int batchSize) {
        List<Object> generatedKeys = new ArrayList<>();

        if (rows.isEmpty() || columns.isEmpty()) {
            return generatedKeys;
        }

        String sql = buildInsertSql(tableName, columns);
        log.info("Writing {} rows to table {}", rows.size(), tableName);

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int count = 0;
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    setParameter(ps, i + 1, value);
                }
                ps.addBatch();
                count++;

                if (count % batchSize == 0) {
                    ps.executeBatch();
                    extractGeneratedKeys(ps, generatedKeys);
                }
            }

            if (count % batchSize != 0) {
                ps.executeBatch();
                extractGeneratedKeys(ps, generatedKeys);
            }

            log.info("Successfully wrote {} rows to table {}", rows.size(), tableName);
        } catch (SQLException e) {
            throw new RuntimeException("写入数据到表 " + tableName + " 失败: " + e.getMessage(), e);
        }

        return generatedKeys;
    }

    /**
     * Write generated rows to the target database (独立事务，单表写入).
     * Returns the list of auto-generated keys (for auto_increment PKs).
     */
    public List<Object> writeRows(Long connectionId, String tableName, List<String> columns,
                                   List<Map<String, Object>> rows, int batchSize) {
        List<Object> generatedKeys = new ArrayList<>();

        if (rows.isEmpty() || columns.isEmpty()) {
            return generatedKeys;
        }

        try (Connection conn = connectionService.getConnection(connectionId)) {
            conn.setAutoCommit(false);
            try {
                generatedKeys = writeRowsOnConnection(conn, tableName, columns, rows, batchSize);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("写入数据到表 " + tableName + " 失败: " + e.getMessage(), e);
        }

        return generatedKeys;
    }

    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `").append(tableName).append("` (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("`").append(columns.get(i)).append("`");
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private void setParameter(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null || "null".equalsIgnoreCase(String.valueOf(value))) {
            ps.setNull(index, Types.NULL);
        } else if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                ps.setNull(index, Types.NULL);
            } else {
                ps.setString(index, str);
            }
        } else if (value instanceof Integer) {
            ps.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            ps.setLong(index, (Long) value);
        } else if (value instanceof Double) {
            ps.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            ps.setFloat(index, (Float) value);
        } else if (value instanceof Boolean) {
            ps.setBoolean(index, (Boolean) value);
        } else if (value instanceof java.math.BigDecimal) {
            ps.setBigDecimal(index, (java.math.BigDecimal) value);
        } else if (value instanceof LocalDate) {
            ps.setDate(index, Date.valueOf((LocalDate) value));
        } else if (value instanceof LocalDateTime) {
            ps.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
        } else {
            ps.setString(index, value.toString());
        }
    }

    private void extractGeneratedKeys(PreparedStatement ps, List<Object> generatedKeys) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            while (rs.next()) {
                generatedKeys.add(rs.getObject(1));
            }
        }
    }

    /**
     * 表写入任务封装
     */
    public static class TableWriteTask {
        private final String tableName;
        private final List<String> columns;
        private final List<Map<String, Object>> rows;

        public TableWriteTask(String tableName, List<String> columns, List<Map<String, Object>> rows) {
            this.tableName = tableName;
            this.columns = columns;
            this.rows = rows;
        }

        public String getTableName() { return tableName; }
        public List<String> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
    }
}

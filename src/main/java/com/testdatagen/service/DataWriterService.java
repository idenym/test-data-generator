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
            int batchStartIndex = 0;
            List<Map<String, Object>> currentBatch = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    setParameter(ps, i + 1, value);
                }
                ps.addBatch();
                currentBatch.add(row);
                count++;

                if (count % batchSize == 0) {
                    executeBatchWithDiagnostics(ps, tableName, columns, currentBatch, batchStartIndex);
                    extractGeneratedKeys(ps, generatedKeys);
                    currentBatch.clear();
                    batchStartIndex = count;
                }
            }

            if (count % batchSize != 0) {
                executeBatchWithDiagnostics(ps, tableName, columns, currentBatch, batchStartIndex);
                extractGeneratedKeys(ps, generatedKeys);
            }

            log.info("Successfully wrote {} rows to table {}", rows.size(), tableName);
        } catch (SQLException e) {
            throw new RuntimeException("写入数据到表 " + tableName + " 失败: " + e.getMessage(), e);
        }

        return generatedKeys;
    }

    /**
     * 执行批量写入，出错时打印当前批次中所有行的详细数据，便于排查数据截断等问题。
     */
    private void executeBatchWithDiagnostics(PreparedStatement ps, String tableName,
                                              List<String> columns,
                                              List<Map<String, Object>> currentBatch,
                                              int batchStartIndex) throws SQLException {
        try {
            ps.executeBatch();
        } catch (SQLException e) {
            // 从 JDBC 异常消息中解析 "at row N"（1-based）
            int problemRowInBatch = extractProblemRowIndex(e.getMessage());

            StringBuilder diag = new StringBuilder();
            diag.append("批量写入表 ").append(tableName).append(" 失败。");
            diag.append("批次起始行索引=").append(batchStartIndex);
            diag.append(", 批次大小=").append(currentBatch.size());
            diag.append(", JDBC报告问题行=").append(problemRowInBatch);
            diag.append(", 异常=").append(e.getMessage());
            diag.append("\n--- 批次内全部数据（共 ").append(currentBatch.size()).append(" 行）---");

            for (int i = 0; i < currentBatch.size(); i++) {
                Map<String, Object> row = currentBatch.get(i);
                boolean isProblemRow = (problemRowInBatch > 0 && i + 1 == problemRowInBatch);
                diag.append("\n[").append(batchStartIndex + i).append("]");
                if (isProblemRow) diag.append(" <-- JDBC报告的问题行");
                diag.append(" ");
                for (String col : columns) {
                    Object val = row.get(col);
                    String display = val == null ? "NULL" : String.valueOf(val);
                    if (display.length() > 100) {
                        display = display.substring(0, 100) + "...(共" + display.length() + "字符)";
                    }
                    diag.append(col).append("=").append("'").append(display).append("'").append(" |");
                }
            }
            diag.append("\n--- 数据结束 ---");

            log.error(diag.toString());
            throw new SQLException(diag.toString(), e);
        }
    }

    /**
     * 从 JDBC 批量异常消息中提取 "at row N" 中的 N（1-based）。
     * 例如 "Data too long for column 'ent_id' at row 1" -> 返回 1
     */
    private int extractProblemRowIndex(String message) {
        if (message == null) return -1;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("at row (\\d+)");
        java.util.regex.Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
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
                // 尝试将纯数字字符串转为 Long（处理前端回传的大整数字符串）
                if (str.matches("-?\\d+") && str.length() >= 16) {
                    try {
                        ps.setLong(index, Long.parseLong(str));
                        return;
                    } catch (NumberFormatException ignored) {
                        // 超出 Long 范围，按字符串处理
                    }
                }
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

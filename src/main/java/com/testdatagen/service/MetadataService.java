package com.testdatagen.service;

import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.TableMetadata;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class MetadataService {

    private final ConnectionService connectionService;

    public MetadataService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public List<String> listTables(Long connectionId) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = connectionService.getConnection(connectionId)) {
            String dbName = conn.getCatalog();
            String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    public TableMetadata getTableMetadata(Long connectionId, String tableName) {
        try (Connection conn = connectionService.getConnection(connectionId)) {
            return getTableMetadata(conn, tableName);
        } catch (SQLException e) {
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
    }

    public TableMetadata getTableMetadata(Connection conn, String tableName) throws SQLException {
        String dbName = conn.getCatalog();
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);

        // Get table comment
        String tableCommentSql = "SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(tableCommentSql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    table.setTableComment(rs.getString("TABLE_COMMENT"));
                }
            }
        }

        // Get columns
        List<ColumnMetadata> columns = new ArrayList<>();
        String columnSql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, " +
                "IS_NULLABLE, COLUMN_KEY, EXTRA, COLUMN_DEFAULT, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(columnSql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMetadata col = new ColumnMetadata();
                    col.setColumnName(rs.getString("COLUMN_NAME"));
                    col.setDataType(rs.getString("DATA_TYPE"));
                    col.setColumnType(rs.getString("COLUMN_TYPE"));
                    Long maxLen = rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ?
                            rs.getLong("CHARACTER_MAXIMUM_LENGTH") : null;
                    col.setMaxLength(maxLen != null ? maxLen.intValue() : null);
                    col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                    col.setPrimaryKey("PRI".equals(rs.getString("COLUMN_KEY")));
                    col.setAutoIncrement(rs.getString("EXTRA") != null &&
                            rs.getString("EXTRA").contains("auto_increment"));
                    col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                    col.setComment(rs.getString("COLUMN_COMMENT"));
                    columns.add(col);
                }
            }
        }

        // Get foreign keys
        String fkSql = "SELECT COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                "FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND REFERENCED_TABLE_NAME IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
            ps.setString(1, dbName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String refTable = rs.getString("REFERENCED_TABLE_NAME");
                    String refCol = rs.getString("REFERENCED_COLUMN_NAME");
                    columns.stream()
                            .filter(c -> c.getColumnName().equals(colName))
                            .findFirst()
                            .ifPresent(c -> {
                                c.setReferencedTable(refTable);
                                c.setReferencedColumn(refCol);
                            });
                }
            }
        }

        table.setColumns(columns);
        return table;
    }

    public Map<String, List<String[]>> getForeignKeys(Long connectionId) {
        Map<String, List<String[]>> fkMap = new LinkedHashMap<>();
        try (Connection conn = connectionService.getConnection(connectionId)) {
            String dbName = conn.getCatalog();
            String sql = "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                    "FROM information_schema.KEY_COLUMN_USAGE " +
                    "WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        fkMap.computeIfAbsent(tableName, k -> new ArrayList<>())
                                .add(new String[]{
                                        rs.getString("COLUMN_NAME"),
                                        rs.getString("REFERENCED_TABLE_NAME"),
                                        rs.getString("REFERENCED_COLUMN_NAME")
                                });
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取外键关系失败: " + e.getMessage(), e);
        }
        return fkMap;
    }
}

package com.testdatagen.service;

import com.testdatagen.model.dto.ColumnMetadata;
import com.testdatagen.model.dto.TableMetadata;
import com.testdatagen.model.enums.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final ConnectionService connectionService;

    public MetadataService(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public List<String> listTables(Long connectionId) {
        DbType dbType = connectionService.getDbType(connectionId);
        List<String> tables = new ArrayList<>();
        try (Connection conn = connectionService.getConnection(connectionId)) {
            if (dbType == DbType.HIVE) {
                tables = listTablesHive(conn);
            } else if (dbType.isPgCompatible()) {
                tables = listTablesPg(conn);
            } else {
                tables = listTablesMySQL(conn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    public TableMetadata getTableMetadata(Long connectionId, String tableName) {
        DbType dbType = connectionService.getDbType(connectionId);
        try (Connection conn = connectionService.getConnection(connectionId)) {
            if (dbType == DbType.HIVE) {
                return getTableMetadataHive(conn, tableName);
            } else if (dbType.isPgCompatible()) {
                return getTableMetadataPg(conn, tableName);
            } else {
                return getTableMetadataMySQL(conn, tableName);
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
    }

    public Map<String, List<String[]>> getForeignKeys(Long connectionId) {
        DbType dbType = connectionService.getDbType(connectionId);
        Map<String, List<String[]>> fkMap = new LinkedHashMap<>();

        if (dbType == DbType.HIVE) {
            // Hive 不支持外键
            return fkMap;
        }

        try (Connection conn = connectionService.getConnection(connectionId)) {
            if (dbType.isPgCompatible()) {
                fkMap = getForeignKeysPg(conn);
            } else {
                fkMap = getForeignKeysMySQL(conn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取外键关系失败: " + e.getMessage(), e);
        }
        return fkMap;
    }

    // ========== JDBC DatabaseMetaData (通用兼容层) ==========

    public TableMetadata getTableMetadata(Connection conn, String tableName) throws SQLException {
        // 通过 JDBC metadata 获取通用信息
        String dbName = getSchemaName(conn);
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);

        DatabaseMetaData metaData = conn.getMetaData();

        // 获取表注释
        try (ResultSet rs = metaData.getTables(dbName, getSchemaPattern(conn), tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                table.setTableComment(rs.getString("REMARKS"));
            }
        }

        // 获取列信息
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(dbName, getSchemaPattern(conn), tableName, "%")) {
            while (rs.next()) {
                ColumnMetadata col = new ColumnMetadata();
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setDataType(rs.getString("TYPE_NAME"));
                col.setColumnType(rs.getString("TYPE_NAME"));
                int size = rs.getInt("COLUMN_SIZE");
                col.setMaxLength(size > 0 ? size : null);
                // DECIMAL/NUMERIC 的小数位数（非数值列为 0/空）
                int decDigits = rs.getInt("DECIMAL_DIGITS");
                col.setNumericScale(rs.wasNull() ? null : decDigits);
                col.setNullable("YES".equals(rs.getString("IS_NULLABLE")) || rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                // JDBC 标准 getColumns() 的默认值列名为 COLUMN_DEF（非 information_schema 的 COLUMN_DEFAULT）
                col.setDefaultValue(rs.getString("COLUMN_DEF"));
                col.setComment(rs.getString("REMARKS"));
                col.setAutoIncrement("YES".equals(rs.getString("IS_AUTOINCREMENT")));
                columns.add(col);
            }
        }

        // 获取主键
        try (ResultSet rs = metaData.getPrimaryKeys(dbName, getSchemaPattern(conn), tableName)) {
            while (rs.next()) {
                String pkCol = rs.getString("COLUMN_NAME");
                columns.stream()
                        .filter(c -> c.getColumnName().equals(pkCol))
                        .findFirst()
                        .ifPresent(c -> c.setPrimaryKey(true));
            }
        }

        // 获取外键
        try (ResultSet rs = metaData.getImportedKeys(dbName, getSchemaPattern(conn), tableName)) {
            while (rs.next()) {
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String refTable = rs.getString("PKTABLE_NAME");
                String refCol = rs.getString("PKCOLUMN_NAME");
                columns.stream()
                        .filter(c -> c.getColumnName().equals(fkCol))
                        .findFirst()
                        .ifPresent(c -> {
                            c.setReferencedTable(refTable);
                            c.setReferencedColumn(refCol);
                        });
            }
        }

        table.setColumns(columns);
        return table;
    }

    // ========== MySQL / TDSQL 专用 ==========

    private List<String> listTablesMySQL(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
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
        return tables;
    }

    private TableMetadata getTableMetadataMySQL(Connection conn, String tableName) throws SQLException {
        String dbName = conn.getCatalog();
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);

        // 表注释
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

        // 列信息
        List<ColumnMetadata> columns = new ArrayList<>();
        String columnSql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, " +
                "NUMERIC_SCALE, IS_NULLABLE, COLUMN_KEY, EXTRA, COLUMN_DEFAULT, COLUMN_COMMENT " +
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
                    // DECIMAL/NUMERIC 的小数位数（非数值列为 NULL）
                    Integer numScale = rs.getObject("NUMERIC_SCALE") != null ?
                            rs.getInt("NUMERIC_SCALE") : null;
                    col.setNumericScale(numScale);
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

        // 外键
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

    private Map<String, List<String[]>> getForeignKeysMySQL(Connection conn) throws SQLException {
        Map<String, List<String[]>> fkMap = new LinkedHashMap<>();
        String dbName = conn.getCatalog();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME " +
                "FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tbl = rs.getString("TABLE_NAME");
                    fkMap.computeIfAbsent(tbl, k -> new ArrayList<>())
                            .add(new String[]{
                                    rs.getString("COLUMN_NAME"),
                                    rs.getString("REFERENCED_TABLE_NAME"),
                                    rs.getString("REFERENCED_COLUMN_NAME")
                            });
                }
            }
        }
        return fkMap;
    }

    // ========== GaussDB (PostgreSQL) 专用 ==========

    private List<String> listTablesPg(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
            }
        }
        return tables;
    }

    private TableMetadata getTableMetadataPg(Connection conn, String tableName) throws SQLException {
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);

        // 表注释
        String commentSql = "SELECT obj_description(c.oid) AS comment " +
                "FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid " +
                "WHERE n.nspname = 'public' AND c.relname = ? AND c.relkind = 'r'";
        try (PreparedStatement ps = conn.prepareStatement(commentSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    table.setTableComment(rs.getString("comment"));
                }
            }
        }

        // 列信息
        List<ColumnMetadata> columns = new ArrayList<>();
        String colSql = "SELECT c.column_name, c.data_type, c.udt_name, c.character_maximum_length, " +
                "c.numeric_scale, c.is_nullable, c.column_default, " +
                "pgd.description AS column_comment " +
                "FROM information_schema.columns c " +
                "LEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_schema = st.schemaname AND c.table_name = st.relname " +
                "LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position " +
                "WHERE c.table_schema = 'public' AND c.table_name = ? " +
                "ORDER BY c.ordinal_position";
        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnMetadata col = new ColumnMetadata();
                    col.setColumnName(rs.getString("column_name"));
                    String dataType = rs.getString("data_type");
                    col.setDataType(dataType);
                    col.setColumnType(rs.getString("udt_name"));
                    Long maxLen = rs.getObject("character_maximum_length") != null ?
                            rs.getLong("character_maximum_length") : null;
                    col.setMaxLength(maxLen != null ? maxLen.intValue() : null);
                    // NUMERIC/DECIMAL 的小数位数（非数值列为 NULL）
                    Integer numScale = rs.getObject("numeric_scale") != null ?
                            rs.getInt("numeric_scale") : null;
                    col.setNumericScale(numScale);
                    col.setNullable("YES".equals(rs.getString("is_nullable")));
                    String defaultVal = rs.getString("column_default");
                    col.setDefaultValue(defaultVal);
                    col.setAutoIncrement(defaultVal != null && defaultVal.startsWith("nextval"));
                    col.setComment(rs.getString("column_comment"));
                    columns.add(col);
                }
            }
        }

        // 主键
        String pkSql = "SELECT kcu.column_name FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_schema = 'public' AND tc.table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pkCol = rs.getString("column_name");
                    columns.stream()
                            .filter(c -> c.getColumnName().equals(pkCol))
                            .findFirst()
                            .ifPresent(c -> c.setPrimaryKey(true));
                }
            }
        }

        // 外键
        String fkSql = "SELECT kcu.column_name, ccu.table_name AS referenced_table, ccu.column_name AS referenced_column " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name " +
                "AND tc.table_schema = ccu.table_schema " +
                "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' AND tc.table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("column_name");
                    String refTable = rs.getString("referenced_table");
                    String refCol = rs.getString("referenced_column");
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

    private Map<String, List<String[]>> getForeignKeysPg(Connection conn) throws SQLException {
        Map<String, List<String[]>> fkMap = new LinkedHashMap<>();
        String sql = "SELECT tc.table_name, kcu.column_name, ccu.table_name AS referenced_table, " +
                "ccu.column_name AS referenced_column " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "AND tc.table_schema = kcu.table_schema " +
                "JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name " +
                "AND tc.table_schema = ccu.table_schema " +
                "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tbl = rs.getString("table_name");
                    fkMap.computeIfAbsent(tbl, k -> new ArrayList<>())
                            .add(new String[]{
                                    rs.getString("column_name"),
                                    rs.getString("referenced_table"),
                                    rs.getString("referenced_column")
                            });
                }
            }
        }
        return fkMap;
    }

    // ========== Hive 专用 ==========

    private List<String> listTablesHive(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        Collections.sort(tables);
        return tables;
    }

    private TableMetadata getTableMetadataHive(Connection conn, String tableName) throws SQLException {
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);
        List<ColumnMetadata> columns = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("DESCRIBE " + quoteHive(tableName))) {
            while (rs.next()) {
                String colName = rs.getString(1);
                if (colName == null || colName.startsWith("#") || colName.isEmpty()) continue;

                ColumnMetadata col = new ColumnMetadata();
                col.setColumnName(colName.trim());
                String dataType = rs.getString(2);
                col.setDataType(dataType != null ? dataType.trim() : "string");
                col.setColumnType(dataType != null ? dataType.trim() : "string");
                // 从 decimal(p,s) 类型声明中解析精度与小数位，避免生成值超出范围
                parseHiveDecimal(col);
                col.setNullable(true);
                String comment = rs.getString(3);
                if (comment != null && !comment.trim().isEmpty()) {
                    col.setComment(comment.trim());
                }
                columns.add(col);
            }
        }

        table.setColumns(columns);
        return table;
    }

    private String quoteHive(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * 从 Hive 类型声明（如 decimal(6,4)）中解析精度与小数位，填入 maxLength/numericScale。
     */
    private void parseHiveDecimal(ColumnMetadata col) {
        String type = col.getColumnType();
        if (type == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("decimal\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(type);
        if (m.find()) {
            col.setMaxLength(Integer.parseInt(m.group(1)));
            col.setNumericScale(Integer.parseInt(m.group(2)));
        }
    }

    // ========== 辅助方法 ==========

    private String getSchemaName(Connection conn) throws SQLException {
        DbType dbType = detectDbType(conn);
        if (dbType.isPgCompatible()) {
            return "public";
        }
        if (dbType == DbType.HIVE) {
            try {
                return conn.getSchema();
            } catch (Exception e) {
                return conn.getCatalog();
            }
        }
        return conn.getCatalog();
    }

    private String getSchemaPattern(Connection conn) throws SQLException {
        DbType dbType = detectDbType(conn);
        if (dbType.isPgCompatible()) {
            return "public";
        }
        return null;
    }

    private DbType detectDbType(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            if (url.startsWith("jdbc:postgresql:")) return DbType.GAUSSDB;
            if (url.startsWith("jdbc:hive2:")) return DbType.HIVE;
            return DbType.MYSQL;
        } catch (Exception e) {
            return DbType.MYSQL;
        }
    }
}


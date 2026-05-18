package com.testdatagen.test;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SqlParseTest {

    @Test
    public void testEnterpriseScenarioSqlParse() throws Exception {
        String sql = new String(Files.readAllBytes(
            Paths.get("src/main/resources/sql/enterprise-data-scenario.sql")));

        // Extract the last INSERT INTO ... SELECT statement
        int insertIdx = sql.toUpperCase().lastIndexOf("INSERT INTO");
        assertTrue(insertIdx >= 0, "Should contain INSERT statement");
        String insertSql = sql.substring(insertIdx);

        Statement stmt = CCJSqlParserUtil.parse(insertSql);
        assertTrue(stmt instanceof Insert, "Should parse as Insert");

        Insert insert = (Insert) stmt;
        assertEquals("t_full_enterprise_info", insert.getTable().getName());

        Select select = insert.getSelect();
        assertNotNull(select);

        PlainSelect ps = (PlainSelect) select;

        // Check FROM table
        assertTrue(ps.getFromItem() instanceof Table);
        assertEquals("t_enterprise_base", ((Table) ps.getFromItem()).getName());

        // Check JOINs
        List<Join> joins = ps.getJoins();
        assertNotNull(joins);
        System.out.println("JOIN count: " + joins.size());

        Set<String> joinTables = new LinkedHashSet<>();
        for (Join j : joins) {
            FromItem right = j.getRightItem();
            assertTrue(right instanceof Table, "JOIN right item should be Table");
            Table t = (Table) right;
            joinTables.add(t.getName());
            String joinType = j.isLeft() ? "LEFT" : j.isRight() ? "RIGHT" : "INNER";
            System.out.println("  " + joinType + " JOIN " + t.getName() +
                (t.getAlias() != null ? " AS " + t.getAlias().getName() : ""));

            // Check ON expressions exist
            Collection<Expression> onExprs = j.getOnExpressions();
            assertNotNull(onExprs, "JOIN should have ON expressions");
            assertFalse(onExprs.isEmpty(), "JOIN ON should not be empty");
        }

        // Should have 10 JOINs
        assertEquals(10, joins.size(), "Should have 10 JOINs");

        // Verify all expected tables are joined
        Set<String> expectedTables = new LinkedHashSet<>(Arrays.asList(
            "t_merchant_base", "t_customer_identity", "t_legal_person_cert",
            "t_gs_key", "t_gs_key", "t_upstream_code", "t_upstream_code",
            "t_upstream_code", "t_external_code", "t_external_code"
        ));
        assertEquals(expectedTables, joinTables, "Should join expected tables");

        // Check WHERE clause
        Expression where = ps.getWhere();
        assertNotNull(where, "Should have WHERE clause");
        System.out.println("WHERE clause parsed successfully");

        // Check SELECT items count (should be > 80)
        List<SelectItem<?>> items = ps.getSelectItems();
        assertTrue(items.size() >= 80, "Should have at least 80 SELECT columns, got " + items.size());
        System.out.println("SELECT columns: " + items.size());

        System.out.println("All assertions passed!");
    }
}

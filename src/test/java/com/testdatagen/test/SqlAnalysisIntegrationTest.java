package com.testdatagen.test;

import com.testdatagen.model.dto.SqlAnalysisResult;
import com.testdatagen.model.dto.SqlAnalysisResult.WhereHint;
import com.testdatagen.service.SqlParserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SqlAnalysisIntegrationTest {

    @Autowired
    private SqlParserService sqlParserService;

    @Test
    public void testJoinOnLiteralHintsExtracted() {
        // SQL 中包含 JOIN ON 条件里的固定值约束
        String sql = "SELECT * FROM t_enterprise_base e " +
            "LEFT JOIN t_legal_person_cert lp ON e.legal_cert_no = lp.cert_no " +
            "  AND lp.cert_type = 'ID' " +
            "  AND lp.cert_status = '1' " +
            "LEFT JOIN t_merchant_base m ON e.ent_id = m.ent_id " +
            "WHERE e.business_status = '1' " +
            "  AND e.reg_capital >= 0";

        // connectionId=1 使用默认 H2 数据库
        SqlAnalysisResult result = sqlParserService.analyze(1L, sql);

        assertNotNull(result);
        System.out.println("Tables: " + result.getTables());
        System.out.println("Relations: " + result.getRelations().size());
        System.out.println("WhereHints: " + result.getWhereHints().size());

        for (WhereHint h : result.getWhereHints()) {
            System.out.println("  Hint: " + h.getTableName() + "." + h.getColumnName() +
                " -> " + h.getRuleType() + " " + h.getRuleConfig() +
                " [" + h.getDescription() + "]");
        }

        // 应该提取到 WHERE 和 ON 中的固定值
        // WHERE: business_status='1'(ENUM), reg_capital>=0(RANGE)
        // ON: cert_type='ID'(ENUM), cert_status='1'(ENUM)
        assertTrue(result.getWhereHints().size() >= 4,
            "Should extract at least 4 hints from WHERE + ON conditions, got " + result.getWhereHints().size());

        // 验证 ON 条件中的固定值被提取
        Set<String> hintKeys = result.getWhereHints().stream()
            .map(h -> h.getTableName() + "." + h.getColumnName())
            .collect(Collectors.toSet());

        assertTrue(hintKeys.contains("t_legal_person_cert.cert_type"),
            "Should extract lp.cert_type = 'ID' from JOIN ON");
        assertTrue(hintKeys.contains("t_legal_person_cert.cert_status"),
            "Should extract lp.cert_status = '1' from JOIN ON");
        assertTrue(hintKeys.contains("t_enterprise_base.business_status"),
            "Should extract e.business_status = '1' from WHERE");

        // 验证规则类型正确
        List<WhereHint> certTypeHints = result.getWhereHints().stream()
            .filter(h -> "cert_type".equalsIgnoreCase(h.getColumnName()))
            .collect(Collectors.toList());
        assertFalse(certTypeHints.isEmpty());
        assertEquals("ENUM", certTypeHints.get(0).getRuleType());
        assertTrue(certTypeHints.get(0).getRuleConfig().contains("ID"));
    }

    @Test
    public void testEnterpriseScenarioWhereHints() throws Exception {
        String sql = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/main/resources/sql/enterprise-data-scenario.sql")));
        int insertIdx = sql.toUpperCase().lastIndexOf("INSERT INTO");
        String insertSql = sql.substring(insertIdx);

        SqlAnalysisResult result = sqlParserService.analyze(1L, insertSql);

        assertNotNull(result);
        System.out.println("Enterprise scenario - Tables: " + result.getTables());
        System.out.println("Enterprise scenario - WhereHints: " + result.getWhereHints().size());

        for (WhereHint h : result.getWhereHints()) {
            System.out.println("  " + h.getTableName() + "." + h.getColumnName() +
                " -> " + h.getRuleType() + " " + h.getRuleConfig());
        }

        // 验证复杂 SQL 的 WHERE + ON 条件都被提取
        // WHERE 有: business_status IN, unified_code IS NOT NULL, establish_date >=, reg_capital >=, biz_term_to >, merchant_status IN, cust_status !=
        // ON 有: cert_type='ID', cert_status='1', gk_prov.key_type='PROVINCE', gk_city.key_type='CITY',
        //       uc_type.code_type='ENT_TYPE', uc_type.valid_flag='1', ...等
        assertTrue(result.getWhereHints().size() >= 10,
            "Should extract many hints from complex SQL, got " + result.getWhereHints().size());
    }
}

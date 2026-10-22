package com.mfg.ims.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.service.OpeningBalanceService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * D-17: opening stock never reached the GL because OPENING_BALANCE had no account assignment. The bridge now journals
 * it Dr inventory / Cr 3901 Opening Balance Equity. Deliberately not transactional: the journal is posted after the
 * stock posting commits.
 */
class OpeningBalanceGlIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Opening IT', ?, 'x') RETURNING id
                """, Long.class, td.code("ob"), td.code("ob") + "@example.com");
        String[] authorities = { "INVENTORY_ADJUST", "INVENTORY_VIEW" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OB-GL-1 (D-17): 25 kg opened at 40.0000 books Dr 1401 1,000 / Cr 3901 1,000, balanced, with no missing-journal alert")
    void openingStockIsJournalledAgainstOpeningBalanceEquity() {
        long mark = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.journal_headers", Long.class);
        long alertMark = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.alerts", Long.class);
        Material material = td.material();

        openingBalanceService.loadOpeningBalances(List.of(new OpeningBalanceLine("MATERIAL", material.getId(), null,
                null, WAREHOUSE_ID, LOCATION_ID, "AVAILABLE", new BigDecimal("25.000"), "KG",
                new BigDecimal("40.0000"), "opening")));

        Map<String, BigDecimal> net = new HashMap<>();
        jdbc.query("""
                SELECT a.account_code, SUM(jl.debit_amount - jl.credit_amount) AS net
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jh.id > ? AND jh.status = 'POSTED'
                 GROUP BY a.account_code
                """, rs -> {
            net.put(rs.getString("account_code"), rs.getBigDecimal("net"));
        }, mark);
        assertThat(net.get("1401")).as("inventory is debited").isEqualByComparingTo("1000");
        assertThat(net.get("3901")).as("opening balance equity is credited").isEqualByComparingTo("-1000");
        assertThat(net).as("nothing else moved").hasSize(2);

        List<Long> unbalanced = jdbc.queryForList("""
                SELECT jh.id FROM ims.journal_headers jh JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                 WHERE jh.id > ? GROUP BY jh.id HAVING SUM(jl.debit_amount) <> SUM(jl.credit_amount)
                """, Long.class, mark);
        assertThat(unbalanced).isEmpty();

        Integer missing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.alerts WHERE id > ? AND alert_type = 'MISSING_JOURNAL'
                """, Integer.class, alertMark);
        assertThat(missing).as("no missing-journal alert for the opening posting").isZero();
    }
}

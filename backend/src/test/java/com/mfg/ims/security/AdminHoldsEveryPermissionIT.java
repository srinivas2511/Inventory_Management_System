package com.mfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * The Administrator role holds every permission in the catalogue. A release that seeds new permission codes must
 * grant them to ADMIN in the same migration; this test fails the build when one is forgotten.
 */
class AdminHoldsEveryPermissionIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void everyPermissionIsGrantedToAdmin() {
        List<String> missing = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                 WHERE NOT EXISTS (SELECT 1 FROM ims.role_permissions rp
                                     JOIN ims.roles r ON r.id = rp.role_id
                                    WHERE r.code = 'ADMIN' AND rp.permission_id = p.id)
                 ORDER BY p.code
                """, String.class);

        assertThat(missing)
                .as("permissions the ADMIN role does not hold; grant them in the migration that seeds them")
                .isEmpty();
    }

    @Test
    void adminHoldsTheFormerlyMissingFinanceAndExecutionPermissions() {
        List<String> admin = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                  JOIN ims.role_permissions rp ON rp.permission_id = p.id
                  JOIN ims.roles r ON r.id = rp.role_id
                 WHERE r.code = 'ADMIN'
                """, String.class);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ims.permissions", Long.class);
        System.out.println("ADMIN-PERMISSIONS: " + admin.size() + " of " + total);
        assertThat((long) admin.size()).isEqualTo(total);
        assertThat(admin).contains("GL_VIEW", "GL_POST", "JOURNAL_APPROVE", "AP_MANAGE", "AR_MANAGE", "BANK_RECONCILE",
                "BUDGET_MANAGE", "FINANCE_REPORT", "PERIOD_CLOSE", "PRODUCTION_EXECUTE", "PRODUCTION_ASSIGN",
                "DOWNTIME_RECORD", "MATERIAL_REQUEST", "PROBLEM_REPORT");
    }
}

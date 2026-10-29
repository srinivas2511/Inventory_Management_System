package com.mfg.ims.system;

import com.mfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 17 — ERP hardening.
 *
 * <p>P17-1 GET /api/system/version is public and returns appName + version;
 * P17-2 GET /api/system/settings/public is public and returns known keys;
 * P17-3 V73 performance indexes exist in the database.
 */
class Phase17HardeningIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void p17_1_versionEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").isNotEmpty())
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.time").isNotEmpty());
    }

    @Test
    void p17_2_publicSettingsEndpointReturnsKnownKeys() throws Exception {
        mockMvc.perform(get("/api/system/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").isNotEmpty())
                .andExpect(jsonPath("$.companyName").isNotEmpty());
    }

    @Test
    void p17_3_performanceIndexesExist() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'ims' AND indexname IN "
                + "('ix_journal_headers_period_status','ix_inventory_transactions_date',"
                + "'ix_sales_invoices_customer_date','ix_purchase_orders_supplier_date',"
                + "'ix_ap_invoices_supplier_status','ix_payments_company_date')",
                Integer.class);
        assertThat(count).as("all 6 Phase-17 performance indexes should exist").isEqualTo(6);
    }
}

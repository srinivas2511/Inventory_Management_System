package com.mfg.ims.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.support.AbstractIntegrationTest;

@Transactional
class ConsolidationIT extends AbstractIntegrationTest {

    // CO-1: consolidation report returns 200 with expected structure
    @Test
    @WithMockUser(authorities = "CONSOLIDATION_VIEW")
    void CO1_consolidationReportReturnsStructure() throws Exception {
        mockMvc.perform(get("/api/finance/consolidation/report")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-04-01"))
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.eliminations").isArray())
                .andExpect(jsonPath("$.totalRevenue").exists())
                .andExpect(jsonPath("$.totalExpense").exists())
                .andExpect(jsonPath("$.netIncome").exists());
    }

    // CO-2: unauthorized role gets 403
    @Test
    @WithMockUser(authorities = "INVENTORY_VIEW")
    void CO2_unauthorizedRoleGets403() throws Exception {
        mockMvc.perform(get("/api/finance/consolidation/report")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-30"))
                .andExpect(status().isForbidden());
    }
}

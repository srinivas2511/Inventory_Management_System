package com.mfg.ims.production;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.support.AbstractIntegrationTest;

@Transactional
class CapacityBoardIT extends AbstractIntegrationTest {

    // CB-1: capacity board returns 200 with expected structure (empty on fresh DB)
    @Test
    @WithMockUser(authorities = "CAPACITY_VIEW")
    void CB1_capacityBoardReturnsStructure() throws Exception {
        mockMvc.perform(get("/api/production/capacity-board")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineLoad").isArray())
                .andExpect(jsonPath("$.scheduledOps").isArray());
    }

    // CB-2: schedule-operation with non-existent production operation returns 404
    @Test
    @WithMockUser(authorities = "CAPACITY_MANAGE")
    void CB2_scheduleOperationNotFoundReturns404() throws Exception {
        mockMvc.perform(post("/api/production/schedule-operation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"productionOperationId":999999,"machineId":1,
                             "targetDate":"2026-11-01","plannedHours":8}
                            """))
                .andExpect(status().isNotFound());
    }

    // CB-3: unauthorized role cannot view capacity board
    @Test
    @WithMockUser(authorities = "INVENTORY_VIEW")
    void CB3_unauthorizedRoleGets403() throws Exception {
        mockMvc.perform(get("/api/production/capacity-board")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-30"))
                .andExpect(status().isForbidden());
    }
}

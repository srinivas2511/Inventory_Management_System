package com.springmfg.ims.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springmfg.ims.support.AbstractIntegrationTest;

/** Phase 0 end-to-end smoke test: real context + real PostgreSQL + Flyway + security. */
class SystemControllerIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pingIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/api/system/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("ims"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void everythingElseRequiresAuthenticationAndAnswersWithAProblem() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void flywayBaselineWasAppliedInTheImsSchema() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from ims.flyway_schema_history where success", Integer.class);
        Integer trigram = jdbc.queryForObject(
                "select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
        assertThat(trigram).isEqualTo(1);
    }
}

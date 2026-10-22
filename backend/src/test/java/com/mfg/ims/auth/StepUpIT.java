package com.mfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * R9-7.9 / SEC-010: with step-up switched on, the trial balance and AP payments need a recent re-authentication.
 * Real login and HTTP against PostgreSQL; not {@code @Transactional}.
 */
class StepUpIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Temp#Start-2026";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    @Autowired UserAdminService userAdminService;
    @Autowired SystemSettingService settings;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void reset() {
        settings.update("security.stepup.enabled", "false");
        settings.update("security.stepup.minutes", "5");
        String like = "stepit-%";
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    private String adminToken() throws Exception {
        String username = "stepit-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Step Up", username + "@step.test", null,
                PASSWORD, Set.of("ADMIN")));
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private Long periodId() {
        return jdbc.queryForObject("SELECT min(id) FROM ims.fiscal_periods", Long.class);
    }

    private static String credential(String value) {
        return "{\"credential\":\"" + value + "\"}";
    }

    @Test
    @DisplayName("STEP-1: switched off (the default), the trial balance and payments need nothing extra")
    void offByDefault() throws Exception {
        String admin = adminToken();

        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", String.valueOf(periodId()))
                .header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/step-up").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.required").value(false));
    }

    @Test
    @DisplayName("STEP-2: switched on, the trial balance and a payment are refused with STEP_UP_REQUIRED until the user re-authenticates")
    void refusedUntilReauthenticated() throws Exception {
        settings.update("security.stepup.enabled", "true");
        String admin = adminToken();

        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", String.valueOf(periodId()))
                .header("Authorization", admin))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mockMvc.perform(post("/api/ap/invoices/999999/payments").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentDate\":\"2012-01-15\",\"amount\":1,\"paymentMode\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        mockMvc.perform(get("/api/auth/step-up").header("Authorization", admin))
                .andExpect(jsonPath("$.required").value(true)).andExpect(jsonPath("$.validUntil").doesNotExist());
    }

    @Test
    @DisplayName("STEP-3: a wrong credential changes nothing; the right one opens the window for both actions; it expires")
    void reauthenticationOpensAWindowThatExpires() throws Exception {
        settings.update("security.stepup.enabled", "true");
        String admin = adminToken();
        String period = String.valueOf(periodId());

        mockMvc.perform(post("/api/auth/step-up").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(credential("Wrong#Password-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", period).header("Authorization", admin))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/step-up").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(credential(PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validUntil").exists());
        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", period).header("Authorization", admin))
                .andExpect(status().isOk());
        // the payment passes the step-up check and then stops where it should: no such invoice
        mockMvc.perform(post("/api/ap/invoices/999999/payments").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentDate\":\"2012-01-15\",\"amount\":1,\"paymentMode\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE users SET step_up_until = now() - interval '1 minute' WHERE username LIKE 'stepit-%'");
        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", period).header("Authorization", admin))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
    }

    @Test
    @DisplayName("STEP-4: one user's re-authentication does not cover another user, and the window length is a setting")
    void elevationIsPerUser() throws Exception {
        settings.update("security.stepup.enabled", "true");
        settings.update("security.stepup.minutes", "10");
        String first = adminToken();
        String second = adminToken();
        String period = String.valueOf(periodId());

        MvcResult res = mockMvc.perform(post("/api/auth/step-up").header("Authorization", first)
                .contentType(MediaType.APPLICATION_JSON).content(credential(PASSWORD))).andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<String>read(res.getResponse().getContentAsString(), "$.validUntil")).isNotBlank();
        Long minutesLeft = jdbc.queryForObject("""
                SELECT max(extract(epoch FROM (step_up_until - now())) / 60)::bigint FROM ims.users WHERE username LIKE 'stepit-%'
                """, Long.class);
        assertThat(minutesLeft).isBetween(8L, 10L);

        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", period).header("Authorization", first))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gl/trial-balance").param("periodId", period).header("Authorization", second))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
    }

    @Test
    @DisplayName("STEP-5: the credential never reaches the audit log; grants and failures are recorded")
    void theCredentialIsNeverAudited() throws Exception {
        settings.update("security.stepup.enabled", "true");
        String admin = adminToken();
        mockMvc.perform(post("/api/auth/step-up").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(credential("Wrong#Password-1")));
        mockMvc.perform(post("/api/auth/step-up").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(credential(PASSWORD)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'STEP_UP_GRANTED'", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'STEP_UP_FAILED'", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ims.audit_logs
                 WHERE action LIKE 'STEP_UP%'
                   AND (COALESCE(old_value::text, '') || COALESCE(new_value::text, '') || COALESCE(reason, '')) LIKE ?
                """, Integer.class, "%" + PASSWORD + "%")).isZero();
    }
}

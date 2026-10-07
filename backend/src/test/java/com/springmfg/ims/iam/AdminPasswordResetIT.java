package com.springmfg.ims.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * An administrator's password reset and unlock, end to end against PostgreSQL with real sign-ins. Not
 * {@code @Transactional}: what matters is what is committed and what the very next request sees.
 */
class AdminPasswordResetIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Reset#Start-2026";
    private static final String COOKIE = "refresh_token";
    private static final String UPDATE_ONLY = "PWR_UPDATE_ONLY";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private record Session(String accessToken, Cookie refreshCookie) {}

    @BeforeEach
    void role() {
        cleanUp();
        jdbc.update("INSERT INTO roles (code, name) VALUES (?, ?)", UPDATE_ONLY, UPDATE_ONLY);
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) "
                + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?",
                UPDATE_ONLY, "USER_UPDATE");
    }

    @AfterEach
    void cleanUp() {
        String like = "pwreset-%";
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code LIKE 'PWR_%')");
        jdbc.update("DELETE FROM roles WHERE code LIKE 'PWR_%'");
    }

    /** A user who has already passed the first-login change, so the only thing under test is the reset. */
    private String newUser(String role) {
        String username = "pwreset-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Reset Subject", username + "@pwreset.test",
                null, PASSWORD, Set.of(role)));
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);
        return username;
    }

    private long idOf(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private MvcResult tryLogin(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private Session login(String username, String password) throws Exception {
        MvcResult res = tryLogin(username, password);
        assertThat(res.getResponse().getStatus()).as("login as %s", username).isEqualTo(200);
        return new Session(JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken"),
                res.getResponse().getCookie(COOKIE));
    }

    private static String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private MvcResult reset(Session admin, long targetId, String body, int expected) throws Exception {
        return mockMvc.perform(post("/api/admin/users/" + targetId + "/reset-password")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn();
    }

    @Test
    void aGeneratedPasswordIsReturnedOnceEndsEverySessionAndForcesAChangeAtOnce() throws Exception {
        Session admin = login(newUser("ADMIN"), PASSWORD);
        String target = newUser("OPERATOR");
        Session before = login(target, PASSWORD);
        // the target is working normally, so their cached user is unflagged
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(before))).andExpect(status().isOk());

        MvcResult res = reset(admin, idOf(target), "{\"reason\":\"Forgot it\"}", 200);
        String body = res.getResponse().getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");
        assertThat((Boolean) JsonPath.read(body, "$.generated")).isTrue();
        assertThat(temporary).hasSizeGreaterThanOrEqualTo(16).matches(".*[A-Z].*").matches(".*[a-z].*")
                .matches(".*\\d.*").matches(".*[^A-Za-z0-9].*").doesNotContain(target);

        // every session of the target is over: the old password, the old refresh token and the old access token
        assertThat(tryLogin(target, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(post("/api/auth/refresh").cookie(before.refreshCookie())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(before))).andExpect(status().isUnauthorized());

        // the temporary password works, and the very next request already sees the flag (no waiting for a cache TTL)
        Session fresh = login(target, temporary);
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE username = ?", Boolean.class,
                target)).isTrue();
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", bearer(fresh)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(fresh))).andExpect(status().isOk());

        // the one-time password is the only place it exists in the clear: stored hashed, and not in the audit row
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, target))
                .startsWith("$2").doesNotContain(temporary);
        List<Map<String, Object>> audit = jdbc.queryForList("SELECT old_value, new_value, reason FROM audit_logs "
                + "WHERE action = 'ADMIN_PASSWORD_RESET' AND entity_id = ?", String.valueOf(idOf(target)));
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("reason")).isEqualTo("Forgot it");
        assertThat(String.valueOf(audit.get(0))).doesNotContain(temporary);
    }

    @Test
    void anAdministratorCanSupplyThePasswordButItMustMeetThePolicy() throws Exception {
        Session admin = login(newUser("ADMIN"), PASSWORD);
        String target = newUser("OPERATOR");

        reset(admin, idOf(target), "{\"temporaryPassword\":\"short\",\"reason\":\"x\"}", 400);
        assertThat(tryLogin(target, PASSWORD).getResponse().getStatus()).as("unchanged after a refused reset")
                .isEqualTo(200);

        MvcResult res = reset(admin, idOf(target), "{\"temporaryPassword\":\"Given#Temp-2026\",\"reason\":\"x\"}", 200);
        assertThat((Boolean) JsonPath.read(res.getResponse().getContentAsString(), "$.generated")).isFalse();
        // the administrator already knows it, so the response does not carry a password at all
        assertThat(res.getResponse().getContentAsString()).doesNotContain("temporaryPassword");
        assertThat(tryLogin(target, "Given#Temp-2026").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aReasonIsRequired() throws Exception {
        Session admin = login(newUser("ADMIN"), PASSWORD);
        reset(admin, idOf(newUser("OPERATOR")), "{}", 400);
    }

    @Test
    void anAdministratorCannotResetTheirOwnPassword() throws Exception {
        String adminName = newUser("ADMIN");
        Session admin = login(adminName, PASSWORD);

        mockMvc.perform(post("/api/admin/users/" + idOf(adminName) + "/reset-password")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"me\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_PASSWORD_RESET"));
        assertThat(tryLogin(adminName, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void userUpdateAloneCannotResetPasswordsButCanUnlock() throws Exception {
        Session updater = login(newUser(UPDATE_ONLY), PASSWORD);
        String target = newUser("OPERATOR");

        reset(updater, idOf(target), "{\"reason\":\"x\"}", 403);
        mockMvc.perform(post("/api/admin/users/" + idOf(target) + "/unlock").header("Authorization", bearer(updater)))
                .andExpect(status().isOk());
    }

    @Test
    void unlockClearsTheLockoutAndIsAudited() throws Exception {
        Session admin = login(newUser("ADMIN"), PASSWORD);
        String target = newUser("OPERATOR");
        jdbc.update("UPDATE users SET failed_attempts = 5, locked_until = now() + interval '15 minutes' "
                + "WHERE username = ?", target);
        assertThat(tryLogin(target, PASSWORD).getResponse().getStatus()).as("locked").isEqualTo(423);

        mockMvc.perform(post("/api/admin/users/" + idOf(target) + "/unlock").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(false));

        assertThat(tryLogin(target, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'UNLOCK' AND entity_id = ?",
                Long.class, String.valueOf(idOf(target)))).isEqualTo(1L);
    }

    @Test
    void theUserListShowsWhoIsLocked() throws Exception {
        Session admin = login(newUser("ADMIN"), PASSWORD);
        String target = newUser("OPERATOR");
        jdbc.update("UPDATE users SET failed_attempts = 5, locked_until = now() + interval '15 minutes' "
                + "WHERE username = ?", target);

        mockMvc.perform(get("/api/admin/users/" + idOf(target)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
    }
}

package com.springmfg.ims.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.auth.PasswordResetRequested;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/**
 * Phase 1 acceptance: "Audit rows exist for logins (success/failure) and for every user/role change." Everything
 * is checked in the real {@code audit_logs} table.
 */
@RecordApplicationEvents
class AuthAndAdminAuditIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String TEMP = "Temp-Pass-12345!";

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ApplicationEvents events;

    private void login(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private List<Map<String, Object>> rows(String username) {
        return jdbc.queryForList("SELECT * FROM audit_logs WHERE username = ? ORDER BY id", username);
    }

    private List<String> actions(String username) {
        return rows(username).stream().map(r -> (String) r.get("action")).toList();
    }

    // ------------------------------------------------------------------------------------------- authentication

    @Test
    void successfulAndFailedLoginsAreAudited() throws Exception {
        User user = testUsers.create("OPERATOR");
        login(user.getUsername(), "Wrong-Pass-1234!");
        login(user.getUsername(), TestUsers.PASSWORD);

        List<Map<String, Object>> rows = rows(user.getUsername());
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("LOGIN_FAILURE", "LOGIN_SUCCESS");
        assertThat(rows.get(0).get("user_id")).isEqualTo(user.getId());
        assertThat(rows.get(0).get("roles")).isEqualTo("OPERATOR");
        assertThat(rows.get(0).get("ip_address")).isEqualTo("127.0.0.1");
        assertThat(jdbc.queryForObject("SELECT new_value ->> 'detail' FROM audit_logs WHERE id = ?", String.class, rows.get(0).get("id")))
                .isEqualTo("wrong password");
        assertThat(rows.get(1).get("entity_id")).isEqualTo(String.valueOf(user.getId()));
    }

    @Test
    void theFailureIsAuditedEvenThoughTheRequestEndsInAnError() throws Exception {
        // the failed-login counter and the audit row both survive the 401 (no rollback on authentication failures)
        User user = testUsers.create("SALES");
        for (int i = 0; i < 5; i++) {
            login(user.getUsername(), "Wrong-Pass-1234!");
        }
        assertThat(actions(user.getUsername())).containsExactly("LOGIN_FAILURE", "LOGIN_FAILURE", "LOGIN_FAILURE",
                "LOGIN_FAILURE", "LOGIN_FAILURE", "ACCOUNT_LOCKED");
        login(user.getUsername(), TestUsers.PASSWORD); // refused while locked
        assertThat(actions(user.getUsername())).endsWith("LOGIN_FAILURE");
        assertThat(jdbc.queryForObject("SELECT new_value ->> 'detail' FROM audit_logs WHERE username = ? ORDER BY id DESC LIMIT 1",
                String.class, user.getUsername())).isEqualTo("account locked");
    }

    @Test
    void unknownUsersAreRecordedButOnlyIfTheNameLooksLikeAUsername() throws Exception {
        String ghost = "ghost.user" + SEQ.incrementAndGet();
        String notAUsername = "Oops@Typed!Password" + SEQ.get();
        login(ghost, "Wrong-Pass-1234!");
        login(notAUsername, "Wrong-Pass-1234!");

        assertThat(rows(ghost)).hasSize(1);
        assertThat(rows(ghost).get(0).get("user_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE username LIKE ?", Integer.class, "%" + notAUsername + "%"))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE username = '(invalid)'", Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sessionAndPasswordEventsAreAudited() throws Exception {
        User user = testUsers.create("SALES");
        Api.Session session = Api.login(mockMvc, json, user.getUsername(), TestUsers.PASSWORD);

        // refresh token reuse
        String first = session.refreshToken();
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("ims_refresh", first))).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("ims_refresh", first))).andExpect(status().isUnauthorized());
        // logout
        String second = Api.login(mockMvc, json, user.getUsername(), TestUsers.PASSWORD).refreshToken();
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("ims_refresh", second))).andExpect(status().isNoContent());
        // voluntary change
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "currentPassword", TestUsers.PASSWORD,
                        "newPassword", "Brand-New-Pass-77!")))).andExpect(status().isOk());
        // forgot + reset
        events.clear();
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", user.getEmail())))).andExpect(status().isAccepted());
        String token = events.stream(PasswordResetRequested.class).findFirst().orElseThrow().rawToken();
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "newPassword", "Another-New-Pass-88!"))))
                .andExpect(status().isNoContent());

        Set<String> actions = new HashSet<>(actions(user.getUsername()));
        assertThat(actions).contains("LOGIN_SUCCESS", "TOKEN_REUSE_DETECTED", "LOGOUT", "PASSWORD_CHANGED",
                "PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_COMPLETED");
        // no secret ever reaches the table
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE username = ? AND (coalesce(new_value::text, '') || coalesce(old_value::text, '')
                    || coalesce(reason, '')) ~ ?""", Integer.class, user.getUsername(), token + "|Brand-New|Another-New|" + TestUsers.PASSWORD)).isZero();
    }

    // ------------------------------------------------------------------------------------------- administration

    @Test
    void everyUserAndRoleChangeIsAuditedWithTheActingAdmin() throws Exception {
        User admin = testUsers.create("ADMIN");
        String token = Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
        String username = "aud.user" + SEQ.incrementAndGet();
        String roleCode = "AUD_ROLE_" + SEQ.get();

        long userId = json.readTree(mockMvc.perform(Api.bearer(Api.jsonBody(post("/api/users"), json, Map.of("username", username,
                "fullName", "A B", "email", username + "@example.com", "roles", List.of("SALES"), "temporaryPassword", TEMP)), token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode created = json.readTree(mockMvc.perform(Api.bearer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/" + userId), token))
                .andReturn().getResponse().getContentAsString());
        Map<String, Object> update = new LinkedHashMap<>(Map.of("fullName", "Renamed", "email", username + "@example.com", "version", created.get("version").asLong()));
        mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/users/" + userId), json, update), token)).andExpect(status().isOk());
        mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/users/" + userId + "/roles"), json,
                Map.of("roles", List.of("ENGINEER"), "reason", "promoted")), token)).andExpect(status().isOk());
        mockMvc.perform(Api.bearer(Api.jsonBody(post("/api/users/" + userId + "/reset-password"), json,
                Map.of("temporaryPassword", "Reset-Pass-54321!", "reason", "forgot")), token)).andExpect(status().isNoContent());
        mockMvc.perform(Api.bearer(delete("/api/users/" + userId).param("reason", "left"), token)).andExpect(status().isNoContent());
        mockMvc.perform(Api.bearer(post("/api/users/" + userId + "/activate"), token)).andExpect(status().isOk());

        long roleId = json.readTree(mockMvc.perform(Api.bearer(Api.jsonBody(post("/api/roles"), json, Map.of("code", roleCode,
                "name", "R", "permissions", List.of("PRODUCT_VIEW"))), token)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/roles/" + roleId), json, Map.of("name", "R2", "version", 0)), token))
                .andExpect(status().isOk());
        mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/roles/" + roleId + "/permissions"), json,
                Map.of("permissions", List.of("PRODUCT_VIEW", "REPORT_VIEW"), "reason", "reports")), token)).andExpect(status().isOk());
        mockMvc.perform(Api.bearer(delete("/api/roles/" + roleId), token)).andExpect(status().isNoContent());

        List<Map<String, Object>> userRows = jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE entity = 'User' AND entity_id = ? ORDER BY id", String.valueOf(userId));
        assertThat(userRows).extracting(r -> r.get("action")).containsExactly("USER_CREATED", "USER_UPDATED",
                "USER_ROLES_CHANGED", "USER_PASSWORD_RESET", "USER_DEACTIVATED", "USER_ACTIVATED");
        List<Map<String, Object>> roleRows = jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE entity = 'Role' AND entity_id = ? ORDER BY id", String.valueOf(roleId));
        assertThat(roleRows).extracting(r -> r.get("action")).containsExactly("ROLE_CREATED", "ROLE_UPDATED",
                "ROLE_PERMISSIONS_CHANGED", "ROLE_DELETED");

        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT * FROM audit_logs WHERE (entity = 'User' AND entity_id = ?) OR (entity = 'Role' AND entity_id = ?)""",
                String.valueOf(userId), String.valueOf(roleId))) {
            assertThat(row.get("user_id")).as("%s actor", row.get("action")).isEqualTo(admin.getId());
            assertThat(row.get("username")).isEqualTo(admin.getUsername());
            assertThat(row.get("roles")).isEqualTo("ADMIN");
            assertThat(row.get("ip_address")).isEqualTo("127.0.0.1");
        }
        // old and new values and the reason are captured
        Map<String, Object> rolesChange = userRows.get(2);
        assertThat(jdbc.queryForObject("SELECT old_value -> 'roles' ->> 0 FROM audit_logs WHERE id = ?", String.class, rolesChange.get("id"))).isEqualTo("SALES");
        assertThat(jdbc.queryForObject("SELECT new_value -> 'roles' ->> 0 FROM audit_logs WHERE id = ?", String.class, rolesChange.get("id"))).isEqualTo("ENGINEER");
        assertThat(rolesChange.get("reason")).isEqualTo("promoted");
        assertThat(roleRows.get(2).get("reason")).isEqualTo("reports");
        assertThat(roleRows.get(3).get("reason")).isNull();

        // passwords, temporary or hashed, appear nowhere
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE ((entity = 'User' AND entity_id = ?) OR (entity = 'Role' AND entity_id = ?))
                  AND (coalesce(new_value::text,'') || coalesce(old_value::text,'')) ~ ?""", Integer.class,
                String.valueOf(userId), String.valueOf(roleId), "Temp-Pass|Reset-Pass|\\$2a\\$")).isZero();
    }
}

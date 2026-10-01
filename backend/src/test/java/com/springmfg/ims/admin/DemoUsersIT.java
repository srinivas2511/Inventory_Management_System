package com.springmfg.ims.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;

/**
 * Phase 1 acceptance: "Every role in the seed can log in". With {@code ims.demo.load-data=true} the application
 * creates the 14 demo users of DESIGN.md 11.2; each must sign in with the documented password, be forced to change
 * it, and then receive exactly the permissions the seed gives their role.
 */
@TestPropertySource(properties = "ims.demo.load-data=true")
class DemoUsersIT extends AbstractIntegrationTest {

    private static final String DEMO_PASSWORD = "Demo@123456!";
    private static final Map<String, String> USERS = Map.ofEntries(
            Map.entry("admin", "ADMIN"), Map.entry("engineer1", "ENGINEER"), Map.entry("prodmgr1", "PRODUCTION_MANAGER"),
            Map.entry("supervisor1", "SUPERVISOR"), Map.entry("operator1", "OPERATOR"), Map.entry("operator2", "OPERATOR"),
            Map.entry("quality1", "QUALITY_MANAGER"), Map.entry("storemgr1", "STORE_MANAGER"),
            Map.entry("storeop1", "STORE_OPERATOR"), Map.entry("purchase1", "PURCHASE_MANAGER"), Map.entry("sales1", "SALES"),
            Map.entry("dispatch1", "DISPATCH"), Map.entry("maint1", "MAINTENANCE"), Map.entry("mgmt1", "MANAGEMENT"));

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    @Test
    void allFourteenDemoUsersExistWithTheirRoleAndMustChangeTheirPassword() throws Exception {
        for (Map.Entry<String, String> demo : USERS.entrySet()) {
            Map<String, Object> row = jdbc.queryForMap("SELECT must_change_password, active, employee_code FROM users WHERE username = ?",
                    demo.getKey());
            assertThat(row.get("must_change_password")).as(demo.getKey()).isEqualTo(true);
            assertThat(row.get("active")).isEqualTo(true);
            List<String> roles = jdbc.queryForList("""
                    SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id JOIN users u ON u.id = ur.user_id
                    WHERE u.username = ?""", String.class, demo.getKey());
            assertThat(roles).containsExactly(demo.getValue());
        }
        assertThat(jdbc.queryForObject("SELECT employee_code FROM users WHERE username = 'supervisor1'", String.class)).isEqualTo("S001");
    }

    @Test
    void everyRoleCanSignInAndGetsExactlyItsSeededPermissions() throws Exception {
        for (Map.Entry<String, String> demo : USERS.entrySet()) {
            String username = demo.getKey();
            JsonNode first = json.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "password", DEMO_PASSWORD))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                    .andReturn().getResponse().getContentAsString());
            assertThat(first.has("accessToken")).as("no session before the password is changed").isFalse();

            String token = json.readTree(mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "currentPassword", DEMO_PASSWORD,
                            "newPassword", "Changed-Demo-Pw-2026!"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();

            Set<String> expected = Set.copyOf(jdbc.queryForList("""
                    SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                    JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ?""", String.class, demo.getValue()));
            JsonNode me = json.readTree(mockMvc.perform(Api.bearer(get("/api/auth/me"), token)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            Set<String> actual = new java.util.HashSet<>();
            me.get("permissions").forEach(n -> actual.add(n.asText()));
            assertThat(actual).as(username).isEqualTo(expected).isNotEmpty();
            assertThat(me.get("roles").get(0).asText()).isEqualTo(demo.getValue());
        }
    }

    @Test
    void loadingAgainDoesNotDuplicateOrResetUsers() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM users WHERE username IN ('admin','operator1')", Integer.class);
        assertThat(before).isEqualTo(2);
    }
}

package com.mfg.ims.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * E2E D-35: the operational endpoints (metrics, and prometheus where the production profile exposes it) reveal JVM, HTTP
 * and connection-pool internals, so only a holder of SYSTEM_MONITOR (the Administrator) may read them. The test profile
 * uses the production-like exposure (health, info, metrics, prometheus); users are real database users who log in.
 * Swagger / OpenAPI stay off outside the dev profile (SWAGGER_ENABLED defaults to false).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorExposureIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Actu#Test-2026";

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String adminToken;
    private String storeToken;

    @BeforeAll
    void createUsersAndLogIn() throws Exception {
        var actor = new CachedUser(9002L, "actuator-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(name("admin"), null, "Actuator admin",
                    name("admin") + "@actuator.test", null, PASSWORD, Set.of("ADMIN")));
            userAdminService.create(new CreateUserRequest(name("store"), null, "Actuator store manager",
                    name("store") + "@actuator.test", null, PASSWORD, Set.of("STORE_MANAGER")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE email LIKE ?", "%@actuator.test");
        adminToken = login(name("admin"));
        storeToken = login(name("store"));
    }

    @AfterAll
    void removeTheUsers() {
        String like = "actu_%_" + runId;
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    private String name(String kind) {
        return "actu_" + kind + "_" + runId;
    }

    private String login(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
        assertThat(login.getResponse().getStatus()).as("login as %s", username).isEqualTo(200);
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private int status(String path, String token) throws Exception {
        var req = get(path);
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("SEC-08a: health and info stay public, as documented")
    void healthAndInfoArePublic() throws Exception {
        assertThat(status("/actuator/health", null)).isEqualTo(200);
        assertThat(status("/actuator/info", null)).isEqualTo(200);
    }

    @Test
    @DisplayName("SEC-08b: metrics need SYSTEM_MONITOR - anonymous 401, signed-in non-admin 403, administrator 200")
    void metricsAreForTheAdministratorOnly() throws Exception {
        assertThat(status("/actuator/metrics", null)).isEqualTo(401);
        assertThat(status("/actuator/metrics", storeToken)).isEqualTo(403);
        assertThat(status("/actuator/metrics", adminToken)).isEqualTo(200);
    }

    @Test
    @DisplayName("SEC-08c: prometheus is closed to anonymous and non-admin callers; the administrator is let through")
    void prometheusIsForTheAdministratorOnly() throws Exception {
        assertThat(status("/actuator/prometheus", null)).isEqualTo(401);
        assertThat(status("/actuator/prometheus", storeToken)).isEqualTo(403);
        // 200 once a Prometheus registry is on the classpath; today the pom has none, so the endpoint answers 404.
        assertThat(status("/actuator/prometheus", adminToken)).isIn(200, 404);
    }

    @Test
    @DisplayName("SEC-07: Swagger / OpenAPI are not served outside the dev profile")
    void swaggerAndOpenApiAreOffByDefault() throws Exception {
        assertThat(status("/v3/api-docs", null)).isEqualTo(404);
        assertThat(status("/swagger-ui.html", null)).isEqualTo(404);
    }
}

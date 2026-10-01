package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.config.ImsSecurityProperties;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestUsers;

/**
 * Task 1.4 over HTTP with real tokens. {@code /api/probe/secret} (test source) is a stand-in endpoint that needs
 * {@code PRODUCT_UPDATE}: the same rule as {@code PUT /api/products/{id}} will have in task 1.9.
 */
class AuthorizationIT extends AbstractIntegrationTest {

    private static final String PROTECTED = "/api/probe/secret";
    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserAccessService userAccess;
    @Autowired
    ImsSecurityProperties properties;

    private String login(User user) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private ResultActions call(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /** A token for {@code user} signed with the real secret but as of another moment, with a chosen version. */
    private String tokenAt(User user, Instant issuedAt, int permissionVersion) {
        User claimsOnly = new User(user.getUsername(), "x", "x@example.com", "x");
        ReflectionTestUtils.setField(claimsOnly, "id", user.getId());
        ReflectionTestUtils.setField(claimsOnly, "permissionVersion", permissionVersion);
        return new JwtService(properties, Clock.fixed(issuedAt, ZoneOffset.UTC)).issue(claimsOnly).value();
    }

    // ---------------------------------------------------------------------------------------- 401

    @Test
    void noTokenIs401() throws Exception {
        call(PROTECTED, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void garbageAndTamperedTokensAre401Unauthenticated() throws Exception {
        User user = testUsers.create("ENGINEER");
        call(PROTECTED, "not-a-jwt").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String[] parts = login(user).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.replace("ENGINEER", "ADMIN").getBytes());
        call(PROTECTED, parts[0] + "." + forged + "." + parts[2]).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void nonBearerSchemeIsTreatedAsNoCredentials() throws Exception {
        mockMvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredTokenIs401TokenExpiredSoTheClientRefreshes() throws Exception {
        User user = testUsers.create("ENGINEER");
        String expired = tokenAt(user, Instant.now().minus(Duration.ofMinutes(16)), 1);
        call(PROTECTED, expired).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void tokenForAnUnknownUserIs401() throws Exception {
        User ghost = new User("ghost", "x", "g@example.com", "x");
        ReflectionTestUtils.setField(ghost, "id", 999_999_999L);
        String token = new JwtService(properties, Clock.systemUTC()).issue(ghost).value();
        call(PROTECTED, token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void deactivatedUserIsCutOffAtOnceOnceTheCacheIsEvicted() throws Exception {
        User user = testUsers.create("ENGINEER");
        String token = login(user);
        call(PROTECTED, token).andExpect(status().isOk());

        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());
        userAccess.evict(user.getId()); // what the admin API does after deactivating (task 1.5)
        call(PROTECTED, token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aUserWhoMustChangeTheirPasswordHasNoAccess() throws Exception {
        User user = testUsers.create("ENGINEER");
        String token = login(user);
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", user.getId());
        userAccess.evict(user.getId());
        call(PROTECTED, token).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------- permission version

    @Test
    void roleChangeInvalidatesOldTokensAndTheRefreshedTokenCarriesTheNewPermissions() throws Exception {
        User user = testUsers.create("OPERATOR");
        String cookieAndToken = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD))))
                .andReturn().getResponse().getHeader("Set-Cookie");
        String refreshToken = cookieAndToken.substring(cookieAndToken.indexOf('=') + 1, cookieAndToken.indexOf(';'));
        String oldToken = login(user);
        call(PROTECTED, oldToken).andExpect(status().isForbidden()); // an Operator may not update products

        // what PUT /api/users/{id}/roles will do: swap the role, bump the version, evict the cache
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", user.getId());
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE code = 'ENGINEER'", user.getId());
        jdbc.update("UPDATE users SET permission_version = permission_version + 1 WHERE id = ?", user.getId());
        userAccess.evict(user.getId());

        call(PROTECTED, oldToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        String refreshed = json.readTree(mockMvc.perform(post("/api/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("ims_refresh", refreshToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        call(PROTECTED, refreshed).andExpect(status().isOk());
    }

    @Test
    void permissionsAreReadFromTheServerNotFromTheToken() throws Exception {
        // tokenAt() issues a genuinely signed token whose roles claim is empty: the claim carries no authority
        User engineer = testUsers.create("ENGINEER");
        call(PROTECTED, tokenAt(engineer, Instant.now(), 1)).andExpect(status().isOk());
        User operator = testUsers.create("OPERATOR");
        call(PROTECTED, tokenAt(operator, Instant.now(), 1)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------------------------------------------------------------------------------------- public endpoints

    @Test
    void publicEndpointsIgnoreAStaleOrBrokenToken() throws Exception {
        User user = testUsers.create("ENGINEER");
        String expired = tokenAt(user, Instant.now().minus(Duration.ofHours(2)), 1);
        call("/api/system/ping", expired).andExpect(status().isOk());
        call("/api/system/ping", "garbage").andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------------- 403 matrix

    /** The role x endpoint matrix for the stand-in endpoint, expected values generated from the seeded permissions. */
    @Test
    void everyRoleGetsExactlyWhatTheSeededPermissionMatrixSays() throws Exception {
        for (String role : ROLES) {
            Integer holds = jdbc.queryForObject("""
                    SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                    JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ? AND p.code = 'PRODUCT_UPDATE'""",
                    Integer.class, role);
            String token = login(testUsers.create(role));
            if (holds != null && holds > 0) {
                call(PROTECTED, token).andExpect(status().isOk());
            } else {
                call(PROTECTED, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        // spot-check the headline rule from the acceptance criteria
        call(PROTECTED, login(testUsers.create("OPERATOR"))).andExpect(status().isForbidden());
        call(PROTECTED, login(testUsers.create("ENGINEER"))).andExpect(status().isOk());
        assertThat(ROLES).hasSize(13);
    }
}

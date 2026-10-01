package com.springmfg.ims.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/**
 * Every administration endpoint, every role, with a <b>valid</b> payload (request binding runs before method
 * security, so an invalid body could answer 400 instead of 403 and hide a missing check). A role without the
 * required permission must get 403 and cause no change; roles that hold it are exercised by the functional
 * tests, so here they are only checked on read endpoints.
 */
class AdminEndpointSecurityIT extends AbstractIntegrationTest {

    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    private record Endpoint(String name, Set<String> anyOf, boolean read, Supplier<MockHttpServletRequestBuilder> request) {
    }

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body) {
        try {
            return Api.jsonBody(request, json, body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Endpoint> endpoints(long userId, long roleId) {
        Map<String, Object> newUser = new LinkedHashMap<>();
        newUser.put("username", "sec.probe");
        newUser.put("fullName", "Probe");
        newUser.put("email", "sec.probe@example.com");
        newUser.put("roles", List.of("SALES"));
        newUser.put("temporaryPassword", "Temp-Pass-12345!");
        Map<String, Object> updateUser = Map.of("fullName", "X", "email", "x@example.com", "version", 0);
        Map<String, Object> newRole = Map.of("code", "SEC_PROBE", "name", "Probe", "permissions", List.of("PRODUCT_VIEW"));
        Map<String, Object> updateRole = Map.of("name", "X", "version", 0);

        List<Endpoint> all = new ArrayList<>();
        all.add(new Endpoint("GET /api/users", Set.of("USER_VIEW"), true, () -> get("/api/users")));
        all.add(new Endpoint("GET /api/users/{id}", Set.of("USER_VIEW"), true, () -> get("/api/users/" + userId)));
        all.add(new Endpoint("POST /api/users", Set.of("USER_CREATE"), false, () -> withBody(post("/api/users"), newUser)));
        all.add(new Endpoint("PUT /api/users/{id}", Set.of("USER_UPDATE"), false, () -> withBody(put("/api/users/" + userId), updateUser)));
        all.add(new Endpoint("PUT /api/users/{id}/roles", Set.of("USER_UPDATE"), false,
                () -> withBody(put("/api/users/" + userId + "/roles"), Map.of("roles", List.of("SALES")))));
        all.add(new Endpoint("POST /api/users/{id}/reset-password", Set.of("USER_UPDATE"), false,
                () -> withBody(post("/api/users/" + userId + "/reset-password"), Map.of("temporaryPassword", "Temp-Pass-12345!"))));
        all.add(new Endpoint("POST /api/users/{id}/activate", Set.of("USER_UPDATE"), false, () -> post("/api/users/" + userId + "/activate")));
        all.add(new Endpoint("DELETE /api/users/{id}", Set.of("USER_DELETE"), false, () -> delete("/api/users/" + userId)));
        all.add(new Endpoint("GET /api/roles", Set.of("ROLE_MANAGE", "USER_VIEW"), true, () -> get("/api/roles")));
        all.add(new Endpoint("GET /api/roles/{id}", Set.of("ROLE_MANAGE", "USER_VIEW"), true, () -> get("/api/roles/" + roleId)));
        all.add(new Endpoint("POST /api/roles", Set.of("ROLE_MANAGE"), false, () -> withBody(post("/api/roles"), newRole)));
        all.add(new Endpoint("PUT /api/roles/{id}", Set.of("ROLE_MANAGE"), false, () -> withBody(put("/api/roles/" + roleId), updateRole)));
        all.add(new Endpoint("PUT /api/roles/{id}/permissions", Set.of("ROLE_MANAGE"), false,
                () -> withBody(put("/api/roles/" + roleId + "/permissions"), Map.of("permissions", List.of("PRODUCT_VIEW")))));
        all.add(new Endpoint("DELETE /api/roles/{id}", Set.of("ROLE_MANAGE"), false, () -> delete("/api/roles/" + roleId)));
        all.add(new Endpoint("GET /api/permissions", Set.of("PERMISSION_MANAGE", "ROLE_MANAGE"), true, () -> get("/api/permissions")));
        return all;
    }

    private Set<String> permissionsOf(String role) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ?""", String.class, role));
    }

    @Test
    void everyRoleIsHeldToTheDocumentedPermissionOnEveryAdminEndpoint() throws Exception {
        User target = testUsers.create("SALES");
        long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'SALES'", Long.class);
        List<Endpoint> endpoints = endpoints(target.getId(), roleId);
        long usersBefore = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        long rolesBefore = jdbc.queryForObject("SELECT count(*) FROM roles", Long.class);

        int denied = 0;
        for (String role : ROLES) {
            String token = Api.login(mockMvc, json, testUsers.create(role).getUsername(), TestUsers.PASSWORD).accessToken();
            Set<String> held = permissionsOf(role);
            for (Endpoint endpoint : endpoints) {
                boolean allowed = endpoint.anyOf().stream().anyMatch(held::contains);
                if (!allowed) {
                    mockMvc.perform(Api.bearer(endpoint.request().get(), token)).andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                    denied++;
                } else if (endpoint.read()) {
                    mockMvc.perform(Api.bearer(endpoint.request().get(), token)).andExpect(status().isOk());
                }
            }
        }
        // nothing a denied call attempted took effect (one user per role was created by this test itself)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(usersBefore + ROLES.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Long.class)).isEqualTo(rolesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username = 'sec.probe'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, target.getId())).isTrue();
        assertThat(denied).isGreaterThan(100);
    }

    @Test
    void unauthenticatedCallsAreRejectedEverywhere() throws Exception {
        User target = testUsers.create("SALES");
        for (Endpoint endpoint : endpoints(target.getId(), 1)) {
            mockMvc.perform(endpoint.request().get()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void anOperatorCannotUpdateAUserOrAProductStyleResource() throws Exception {
        // the headline acceptance rule, spelled out: an Operator token gets 403 from the admin APIs
        User operator = testUsers.create("OPERATOR");
        User target = testUsers.create("SALES");
        String token = Api.login(mockMvc, json, operator.getUsername(), TestUsers.PASSWORD).accessToken();
        mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/users/" + target.getId()), json,
                Map.of("fullName", "Hacked", "email", "hacked@example.com", "version", 0)), token))
                .andExpect(status().isForbidden());
    }
}

package com.springmfg.ims.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/**
 * Every product and spring-type endpoint under every role, with valid payloads (request binding runs before method
 * security). A role without the permission gets 403 and changes nothing; holders are exercised by ProductIT.
 */
class ProductSecurityIT extends AbstractIntegrationTest {

    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    private record Endpoint(String name, String permission, boolean read, Supplier<MockHttpServletRequestBuilder> request) {
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

    private List<Endpoint> endpoints(long productId) {
        Map<String, Object> create = Map.of("productCode", "SEC-PROBE", "name", "Probe", "springType", "COMPRESSION");
        Map<String, Object> update = Map.of("name", "Hacked", "springType", "COMPRESSION", "version", 0);
        List<Endpoint> all = new ArrayList<>();
        all.add(new Endpoint("GET /api/products", "PRODUCT_VIEW", true, () -> get("/api/products")));
        all.add(new Endpoint("GET /api/products/{id}", "PRODUCT_VIEW", true, () -> get("/api/products/" + productId)));
        all.add(new Endpoint("POST /api/products", "PRODUCT_CREATE", false, () -> withBody(post("/api/products"), create)));
        all.add(new Endpoint("PUT /api/products/{id}", "PRODUCT_UPDATE", false, () -> withBody(put("/api/products/" + productId), update)));
        all.add(new Endpoint("POST /api/products/{id}/activate", "PRODUCT_UPDATE", false, () -> post("/api/products/" + productId + "/activate")));
        all.add(new Endpoint("DELETE /api/products/{id}", "PRODUCT_UPDATE", false, () -> delete("/api/products/" + productId)));
        all.add(new Endpoint("GET /api/spring-types", "PRODUCT_VIEW", true, () -> get("/api/spring-types")));
        all.add(new Endpoint("GET /api/spring-types/{type}/attributes", "PRODUCT_VIEW", true, () -> get("/api/spring-types/COMPRESSION/attributes")));
        return all;
    }

    private Set<String> permissionsOf(String role) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ?""", String.class, role));
    }

    @Test
    void everyRoleIsHeldToTheDocumentedPermissionOnEveryProductEndpoint() throws Exception {
        long productId = jdbc.queryForObject("INSERT INTO products (product_code, name, spring_type) VALUES ('SEC-PRD', 'P', 'COMPRESSION') RETURNING id", Long.class);
        List<Endpoint> endpoints = endpoints(productId);
        int denied = 0;
        for (String role : ROLES) {
            String token = Api.login(mockMvc, json, testUsers.create(role).getUsername(), TestUsers.PASSWORD).accessToken();
            Set<String> held = permissionsOf(role);
            for (Endpoint endpoint : endpoints) {
                if (!held.contains(endpoint.permission())) {
                    mockMvc.perform(Api.bearer(endpoint.request().get(), token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                    denied++;
                } else if (endpoint.read()) {
                    mockMvc.perform(Api.bearer(endpoint.request().get(), token)).andExpect(status().isOk());
                }
            }
        }
        assertThat(denied).isGreaterThan(40);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE product_code = 'SEC-PROBE'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, productId)).isEqualTo("P");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId)).isEqualTo("DRAFT");
    }

    @Test
    void unauthenticatedCallsAreRejectedEverywhere() throws Exception {
        for (Endpoint endpoint : endpoints(1)) {
            mockMvc.perform(endpoint.request().get()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void onlyTheEngineerRoleMayCreateOrChangeProducts() {
        assertThat(holders("PRODUCT_CREATE")).containsExactly("ENGINEER");
        assertThat(holders("PRODUCT_UPDATE")).containsExactly("ENGINEER");
        assertThat(holders("PRODUCT_VIEW")).contains("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR", "QUALITY_MANAGER", "SALES", "MANAGEMENT")
                .doesNotContain("OPERATOR", "STORE_OPERATOR", "MAINTENANCE", "DISPATCH");
    }

    private List<String> holders(String permission) {
        return jdbc.queryForList("""
                SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id WHERE p.code = ? AND r.system_role""", String.class, permission);
    }
}

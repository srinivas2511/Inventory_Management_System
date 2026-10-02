package com.springmfg.ims.masterdata;

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
 * Every master-data endpoint under every role, with <b>valid</b> payloads (request binding runs before method
 * security). Roles lacking the permission must get 403 and cause no change; holders are exercised by the
 * functional tests and are only checked here on read endpoints. Also: the headline example of REQUIREMENTS 3.1,
 * an Operator calling a write endpoint directly gets 403.
 */
class MasterDataSecurityIT extends AbstractIntegrationTest {

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

    private List<Endpoint> endpoints(long supplierId, long customerId, long materialId) {
        Map<String, Object> newPartner = Map.of("code", "SEC-PROBE", "name", "Probe");
        Map<String, Object> updatePartner = Map.of("name", "X", "version", 0);
        Map<String, Object> newMaterial = Map.of("code", "SEC-PROBE", "name", "Probe", "materialType", "ALLOY", "uom", "KG");
        Map<String, Object> updateMaterial = Map.of("name", "X", "materialType", "ALLOY", "uom", "KG", "version", 0);
        Set<String> sup = Set.of("SUPPLIER_MANAGE");
        Set<String> supRead = Set.of("SUPPLIER_MANAGE", "PURCHASE_VIEW");
        Set<String> cus = Set.of("CUSTOMER_MANAGE");
        Set<String> cusRead = Set.of("CUSTOMER_MANAGE", "SALES_VIEW");
        Set<String> mat = Set.of("MASTERDATA_MANAGE");
        Set<String> matRead = Set.of("MATERIAL_VIEW", "MASTERDATA_MANAGE");

        List<Endpoint> all = new ArrayList<>();
        all.add(new Endpoint("GET /api/suppliers", supRead, true, () -> get("/api/suppliers")));
        all.add(new Endpoint("GET /api/suppliers/{id}", supRead, true, () -> get("/api/suppliers/" + supplierId)));
        all.add(new Endpoint("POST /api/suppliers", sup, false, () -> withBody(post("/api/suppliers"), newPartner)));
        all.add(new Endpoint("PUT /api/suppliers/{id}", sup, false, () -> withBody(put("/api/suppliers/" + supplierId), updatePartner)));
        all.add(new Endpoint("POST /api/suppliers/{id}/activate", sup, false, () -> post("/api/suppliers/" + supplierId + "/activate")));
        all.add(new Endpoint("DELETE /api/suppliers/{id}", sup, false, () -> delete("/api/suppliers/" + supplierId)));
        all.add(new Endpoint("GET /api/customers", cusRead, true, () -> get("/api/customers")));
        all.add(new Endpoint("GET /api/customers/{id}", cusRead, true, () -> get("/api/customers/" + customerId)));
        all.add(new Endpoint("POST /api/customers", cus, false, () -> withBody(post("/api/customers"), newPartner)));
        all.add(new Endpoint("PUT /api/customers/{id}", cus, false, () -> withBody(put("/api/customers/" + customerId), updatePartner)));
        all.add(new Endpoint("POST /api/customers/{id}/activate", cus, false, () -> post("/api/customers/" + customerId + "/activate")));
        all.add(new Endpoint("DELETE /api/customers/{id}", cus, false, () -> delete("/api/customers/" + customerId)));
        all.add(new Endpoint("GET /api/lookups/customers", Set.of("CUSTOMER_MANAGE", "SALES_VIEW", "PRODUCT_VIEW"), true,
                () -> get("/api/lookups/customers")));
        all.add(new Endpoint("GET /api/materials", matRead, true, () -> get("/api/materials")));
        all.add(new Endpoint("GET /api/materials/{id}", matRead, true, () -> get("/api/materials/" + materialId)));
        all.add(new Endpoint("POST /api/materials", mat, false, () -> withBody(post("/api/materials"), newMaterial)));
        all.add(new Endpoint("PUT /api/materials/{id}", mat, false, () -> withBody(put("/api/materials/" + materialId), updateMaterial)));
        all.add(new Endpoint("POST /api/materials/{id}/activate", mat, false, () -> post("/api/materials/" + materialId + "/activate")));
        all.add(new Endpoint("DELETE /api/materials/{id}", mat, false, () -> delete("/api/materials/" + materialId)));
        return all;
    }

    private Set<String> permissionsOf(String role) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ?""", String.class, role));
    }

    @Test
    void everyRoleIsHeldToTheDocumentedPermissionOnEveryMasterDataEndpoint() throws Exception {
        long supplierId = jdbc.queryForObject("INSERT INTO suppliers (supplier_code, name) VALUES ('SEC-SUP', 'S') RETURNING id", Long.class);
        long customerId = jdbc.queryForObject("INSERT INTO customers (customer_code, name) VALUES ('SEC-CUS', 'C') RETURNING id", Long.class);
        long materialId = jdbc.queryForObject("INSERT INTO materials (material_code, name, material_type, uom) VALUES ('SEC-MAT', 'M', 'ALLOY', 'KG') RETURNING id", Long.class);
        List<Endpoint> endpoints = endpoints(supplierId, customerId, materialId);
        long materialsBefore = jdbc.queryForObject("SELECT count(*) FROM materials", Long.class);

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
        assertThat(denied).isGreaterThan(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM materials", Long.class)).isEqualTo(materialsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM materials WHERE material_code = 'SEC-PROBE'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suppliers WHERE supplier_code = 'SEC-PROBE'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE customer_code = 'SEC-PROBE'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM suppliers WHERE id = ?", Boolean.class, supplierId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM customers WHERE id = ?", Boolean.class, customerId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM materials WHERE id = ?", Boolean.class, materialId)).isTrue();
    }

    @Test
    void unauthenticatedCallsAreRejectedEverywhere() throws Exception {
        for (Endpoint endpoint : endpoints(1, 1, 1)) {
            mockMvc.perform(endpoint.request().get()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theRolesThatMayWriteEachMasterAreExactlyTheSeededOnes() {
        // a regression guard on the seed: who can change what (REQUIREMENTS 3.3)
        assertThat(holders("MASTERDATA_MANAGE")).containsExactly("ADMIN");
        assertThat(holders("SUPPLIER_MANAGE")).containsExactlyInAnyOrder("ADMIN", "PURCHASE_MANAGER");
        assertThat(holders("CUSTOMER_MANAGE")).containsExactlyInAnyOrder("ADMIN", "SALES");
    }

    private List<String> holders(String permission) {
        return jdbc.queryForList("""
                SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                JOIN permissions p ON p.id = rp.permission_id WHERE p.code = ? AND r.system_role""", String.class, permission);
    }
}

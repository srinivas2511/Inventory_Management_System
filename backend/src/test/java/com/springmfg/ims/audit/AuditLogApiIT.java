package com.springmfg.ims.audit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/** Task 1.6: {@code GET /api/audit-logs}. Rows are inserted with explicit timestamps under a unique entity name. */
class AuditLogApiIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    private String adminToken() throws Exception {
        return Api.login(mockMvc, json, testUsers.create("ADMIN").getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private void insert(String entity, String entityId, String action, Long userId, String username, String when, String oldJson, String newJson) {
        jdbc.update("""
                INSERT INTO audit_logs (occurred_at, user_id, username, roles, action, entity, entity_id, old_value, new_value, reason, ip_address)
                VALUES (?::timestamptz, ?, ?, 'ADMIN', ?, ?, ?, ?::jsonb, ?::jsonb, 'because', '10.1.1.1')""",
                when, userId, username, action, entity, entityId, oldJson, newJson);
    }

    private ResultActions search(String token, String query) throws Exception {
        return mockMvc.perform(Api.bearer(get("/api/audit-logs" + query), token));
    }

    /** Three rows for one entity name: two actions, three days. */
    private String seed() {
        String entity = "ApiEntity" + SEQ.incrementAndGet();
        insert(entity, "1", "STOCK_ADJUSTMENT", 5L, "alice", "2026-03-01T10:00:00Z", "{\"qty\":500}", "{\"qty\":480}");
        insert(entity, "1", "STOCK_TRANSFER", 6L, "bob", "2026-03-02T10:00:00Z", null, "{\"to\":\"FG-01\"}");
        insert(entity, "2", "STOCK_ADJUSTMENT", 5L, "alice", "2026-03-03T10:00:00Z", "{\"qty\":10}", "{\"qty\":9}");
        return entity;
    }

    @Test
    void returnsNewestFirstWithOldAndNewValues() throws Exception {
        String entity = seed();
        search(adminToken(), "?entity=" + entity).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].entityId").value("2"))
                .andExpect(jsonPath("$.content[0].action").value("STOCK_ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].oldValue.qty").value(10))
                .andExpect(jsonPath("$.content[0].newValue.qty").value(9))
                .andExpect(jsonPath("$.content[0].reason").value("because"))
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].roles").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("10.1.1.1"))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-03-03T10:00:00Z"))
                .andExpect(jsonPath("$.content[1].oldValue").doesNotExist())
                .andExpect(jsonPath("$.content[2].action").value("STOCK_ADJUSTMENT"));
    }

    @Test
    void sortsAscendingOnRequestAndRejectsOtherSortFields() throws Exception {
        String entity = seed();
        String token = adminToken();
        search(token, "?entity=" + entity + "&sort=occurredAt,asc").andExpect(jsonPath("$.content[0].action").value("STOCK_ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].entityId").value("1")).andExpect(jsonPath("$.content[2].entityId").value("2"));
        search(token, "?sort=username,asc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        String entity = seed();
        String token = adminToken();
        search(token, "?entity=" + entity + "&entityId=1").andExpect(jsonPath("$.totalElements").value(2));
        search(token, "?entity=" + entity + "&action=STOCK_TRANSFER").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("bob"));
        search(token, "?entity=" + entity + "&userId=5").andExpect(jsonPath("$.totalElements").value(2));
        search(token, "?entity=" + entity + "&username=ALICE").andExpect(jsonPath("$.totalElements").value(2)); // case-insensitive
        search(token, "?entity=" + entity + "&userId=5&entityId=2&action=STOCK_ADJUSTMENT").andExpect(jsonPath("$.totalElements").value(1));
        search(token, "?entity=" + entity + "&userId=6&action=STOCK_ADJUSTMENT").andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void dateRangeFiltersAreInclusiveAndValidated() throws Exception {
        String entity = seed();
        String token = adminToken();
        search(token, "?entity=" + entity + "&from=2026-03-02T00:00:00Z").andExpect(jsonPath("$.totalElements").value(2));
        search(token, "?entity=" + entity + "&to=2026-03-02T10:00:00Z").andExpect(jsonPath("$.totalElements").value(2));
        search(token, "?entity=" + entity + "&from=2026-03-02T00:00:00Z&to=2026-03-02T23:59:59Z").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("STOCK_TRANSFER"));
        search(token, "?from=2026-03-05T00:00:00Z&to=2026-03-01T00:00:00Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
        search(token, "?from=yesterday").andExpect(status().isBadRequest());
    }

    @Test
    void pagesAndCapsTheSize() throws Exception {
        String entity = seed();
        String token = adminToken();
        search(token, "?entity=" + entity + "&size=2&page=1").andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(2));
        search(token, "?size=1000").andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void filterValuesAreBoundParametersNotSql() throws Exception {
        String token = adminToken();
        search(token, "?action=x'%20OR%20'1'='1").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        search(token, "?username=%27%3B%20DROP%20TABLE%20audit_logs%3B%20--").andExpect(status().isOk());
        jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class); // the table is still there
    }

    @Test
    void onlyRolesHoldingAuditViewMayReadTheLog() throws Exception {
        for (String role : ROLES) {
            User user = testUsers.create(role);
            String token = Api.login(mockMvc, json, user.getUsername(), TestUsers.PASSWORD).accessToken();
            Integer holds = jdbc.queryForObject("""
                    SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                    JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ? AND p.code = 'AUDIT_VIEW'""", Integer.class, role);
            if (holds != null && holds > 0) {
                search(token, "").andExpect(status().isOk());
            } else {
                search(token, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }
}

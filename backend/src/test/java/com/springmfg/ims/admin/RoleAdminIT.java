package com.springmfg.ims.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/** Task 1.5: role and permission administration over HTTP as an Admin. */
@RecordApplicationEvents
class RoleAdminIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    ApplicationEvents events;

    private String adminToken() throws Exception {
        User admin = testUsers.create("ADMIN");
        return Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
            Object body) throws Exception {
        var r = Api.bearer(request, token);
        return mockMvc.perform(body == null ? r : Api.jsonBody(r, json, body));
    }

    private static String newCode() {
        return "TEST_ROLE_" + SEQ.incrementAndGet();
    }

    private JsonNode createRole(String token, String code, String... permissions) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", "Role " + code);
        body.put("description", "created by a test");
        body.put("permissions", List.of(permissions));
        return json.readTree(send(post("/api/roles"), token, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long roleId(String code) {
        return jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, code);
    }

    private AuditCommand lastEvent(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).reduce((a, b) -> b).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------- read

    @Test
    void listShowsThe13SystemRolesWithPermissionAndUserCounts() throws Exception {
        String token = adminToken();
        send(get("/api/roles"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.systemRole == true)].length()").value(org.hamcrest.Matchers.hasSize(13)))
                .andExpect(jsonPath("$[?(@.code == 'OPERATOR')].permissionCount").value(hasItem(2)))
                .andExpect(jsonPath("$[?(@.code == 'ADMIN')].userCount").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void getReturnsSortedPermissionCodes() throws Exception {
        String token = adminToken();
        send(get("/api/roles/" + roleId("OPERATOR")), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("PROBLEM_REPORT"))
                .andExpect(jsonPath("$.permissions[1]").value("PRODUCTION_EXECUTE"))
                .andExpect(jsonPath("$.version").isNumber());
        send(get("/api/roles/999999999"), token, null).andExpect(status().isNotFound());
    }

    @Test
    void permissionCatalogueListsAll75CodesGroupedByModule() throws Exception {
        String token = adminToken();
        send(get("/api/permissions"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(75))
                .andExpect(jsonPath("$[?(@.code == 'PRODUCT_UPDATE')].module").value(hasItem("ENGINEERING")))
                .andExpect(jsonPath("$[0].module").value("ENGINEERING")); // ordered by module, then code
    }

    // ------------------------------------------------------------------------------------------- create / update

    @Test
    void createsACustomRoleAndAuditsIt() throws Exception {
        String token = adminToken();
        String code = newCode();
        Map<String, Object> body = Map.of("code", code, "name", "Shift Lead", "description", "d",
                "permissions", List.of("PRODUCT_VIEW", "REPORT_VIEW"));
        send(post("/api/roles"), token, body).andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/roles/")))
                .andExpect(jsonPath("$.systemRole").value(false)).andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.userCount").value(0));
        AuditCommand event = lastEvent("ROLE_CREATED");
        assertThat(event.newValue()).containsEntry("code", code).containsEntry("permissions", List.of("PRODUCT_VIEW", "REPORT_VIEW"));
    }

    @Test
    void createValidatesCodeDuplicatesAndPermissions() throws Exception {
        String token = adminToken();
        send(post("/api/roles"), token, Map.of("code", "bad code", "name", "x", "permissions", List.of()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
        send(post("/api/roles"), token, Map.of("code", "OPERATOR", "name", "x", "permissions", List.of()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
        send(post("/api/roles"), token, Map.of("code", newCode(), "name", "x", "permissions", List.of("NOT_A_PERMISSION")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("permissions"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("NOT_A_PERMISSION")));
    }

    @Test
    void updateChangesNameAndDescriptionOnlyAndChecksTheVersion() throws Exception {
        String token = adminToken();
        JsonNode created = createRole(token, newCode(), "PRODUCT_VIEW");
        long id = created.get("id").asLong();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", "Renamed");
        update.put("description", "new text");
        update.put("version", created.get("version").asLong());
        send(put("/api/roles/" + id), token, update).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.code").value(created.get("code").asText())).andExpect(jsonPath("$.permissions[0]").value("PRODUCT_VIEW"));
        send(put("/api/roles/" + id), token, update).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    // ------------------------------------------------------------------------------------------- permissions

    @Test
    void changingARolesPermissionsInvalidatesTheTokensOfItsHoldersAtOnce() throws Exception {
        String token = adminToken();
        String code = newCode();
        JsonNode role = createRole(token, code, "PRODUCT_VIEW");
        long id = role.get("id").asLong();

        // a user holding the role, signed in
        User holder = testUsers.create("SALES");
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", holder.getId());
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", holder.getId(), id);
        Api.Session session = Api.login(mockMvc, json, holder.getUsername(), TestUsers.PASSWORD);
        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("MATERIAL_VIEW"))));

        send(put("/api/roles/" + id + "/permissions"), token,
                Map.of("permissions", List.of("PRODUCT_VIEW", "MATERIAL_VIEW"), "reason", "needs materials"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.userCount").value(1));

        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        String refreshed = json.readTree(mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("ims_refresh", session.refreshToken()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(Api.bearer(get("/api/auth/me"), refreshed)).andExpect(jsonPath("$.permissions", hasItem("MATERIAL_VIEW")));

        AuditCommand event = lastEvent("ROLE_PERMISSIONS_CHANGED");
        assertThat(event.oldValue()).containsEntry("permissions", List.of("PRODUCT_VIEW"));
        assertThat(event.newValue()).containsEntry("permissions", List.of("MATERIAL_VIEW", "PRODUCT_VIEW"));
        assertThat(event.reason()).isEqualTo("needs materials");
    }

    @Test
    void unknownPermissionCodesAreRejectedAndNothingChanges() throws Exception {
        String token = adminToken();
        JsonNode role = createRole(token, newCode(), "PRODUCT_VIEW");
        send(put("/api/roles/" + role.get("id").asLong() + "/permissions"), token,
                Map.of("permissions", List.of("PRODUCT_VIEW", "MADE_UP"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("permissions"));
        send(get("/api/roles/" + role.get("id").asLong()), token, null).andExpect(jsonPath("$.permissions.length()").value(1));
    }

    @Test
    void theAdminRoleCannotLoseThePermissionsNeededToRepairIt() throws Exception {
        String token = adminToken();
        long adminRole = roleId("ADMIN");
        for (String essential : List.of("ROLE_MANAGE", "USER_VIEW", "USER_UPDATE")) {
            List<String> without = jdbc.queryForList("""
                    SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = ? AND p.code <> ?""", String.class, adminRole, essential);
            send(put("/api/roles/" + adminRole + "/permissions"), token, Map.of("permissions", without))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ROLE_PROTECTED"));
        }
        Integer stillThere = jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE role_id = ?", Integer.class, adminRole);
        assertThat(stillThere).isEqualTo(37);
    }

    // ------------------------------------------------------------------------------------------- delete

    @Test
    void systemRolesCannotBeDeleted() throws Exception {
        String token = adminToken();
        send(delete("/api/roles/" + roleId("OPERATOR")), token, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_PROTECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = 'OPERATOR'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aCustomRoleNobodyHoldsCanBeDeletedButOneInUseCannot() throws Exception {
        String token = adminToken();
        long free = createRole(token, newCode(), "PRODUCT_VIEW").get("id").asLong();
        send(delete("/api/roles/" + free), token, null).andExpect(status().isNoContent());
        send(get("/api/roles/" + free), token, null).andExpect(status().isNotFound());
        assertThat(lastEvent("ROLE_DELETED").oldValue()).containsKey("code");

        long inUse = createRole(token, newCode(), "PRODUCT_VIEW").get("id").asLong();
        User holder = testUsers.create("SALES");
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", holder.getId(), inUse);
        send(delete("/api/roles/" + inUse), token, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_PROTECTED")).andExpect(jsonPath("$.detail").value(containsString("1 user")));
        send(delete("/api/roles/999999999"), token, null).andExpect(status().isNotFound());
    }
}

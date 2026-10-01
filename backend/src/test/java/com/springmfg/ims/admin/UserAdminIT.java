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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.auth.UserAccessService;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestUsers;

/** Task 1.5: user administration over HTTP as an Admin. */
@RecordApplicationEvents
class UserAdminIT extends AbstractIntegrationTest {

    private static final String TEMP = "Temp-Pass-12345!";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserAccessService userAccess;
    @Autowired
    ApplicationEvents events;

    // ------------------------------------------------------------------------------------------- helpers

    private String adminToken() throws Exception {
        User admin = testUsers.create("ADMIN");
        return Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private static String newName(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private Map<String, Object> body(String username, String... roles) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", username);
        map.put("fullName", "Full " + username);
        map.put("email", username + "@example.com");
        map.put("roles", List.of(roles));
        map.put("temporaryPassword", TEMP);
        return map;
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
            Object body) throws Exception {
        var r = Api.bearer(request, token);
        return mockMvc.perform(body == null ? r : Api.jsonBody(r, json, body));
    }

    private JsonNode createUser(String token, String username, String... roles) throws Exception {
        return json.readTree(send(post("/api/users"), token, body(username, roles)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long countEvents(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).count();
    }

    private AuditCommand lastEvent(String action) {
        return events.stream(AuditCommand.class).filter(e -> e.action().equals(action)).reduce((a, b) -> b).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------- create

    @Test
    void createReturns201WithLocationAndNeverExposesPasswords() throws Exception {
        String token = adminToken();
        String username = newName("newuser");

        var result = send(post("/api/users"), token, body(username, "OPERATOR")).andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/users/")))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContainIgnoringCase("password_hash").doesNotContain("passwordHash").doesNotContain(TEMP);
    }

    @Test
    void newUserSignsInWithTheTemporaryPasswordAndMustChangeIt() throws Exception {
        String token = adminToken();
        String username = newName("newuser");
        createUser(token, username, "SALES");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", TEMP))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "currentPassword", TEMP,
                        "newPassword", "My-Own-Password-9!")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles[0]").value("SALES"));
    }

    @Test
    void duplicateUsernameEmailAndEmployeeCodeAreConflicts() throws Exception {
        String token = adminToken();
        String username = newName("dup");
        Map<String, Object> first = body(username, "SALES");
        first.put("employeeCode", "E" + SEQ.incrementAndGet());
        send(post("/api/users"), token, first).andExpect(status().isCreated());

        Map<String, Object> sameName = body(username.toUpperCase(), "SALES"); // case-insensitive
        sameName.put("email", "other-" + username + "@example.com");
        send(post("/api/users"), token, sameName).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));

        Map<String, Object> sameEmail = body(newName("dup"), "SALES");
        sameEmail.put("email", username.toUpperCase() + "@EXAMPLE.COM");
        send(post("/api/users"), token, sameEmail).andExpect(status().isConflict());

        Map<String, Object> sameCode = body(newName("dup"), "SALES");
        sameCode.put("employeeCode", first.get("employeeCode"));
        send(post("/api/users"), token, sameCode).andExpect(status().isConflict());
    }

    @Test
    void createValidatesInputPerField() throws Exception {
        String token = adminToken();

        Map<String, Object> badName = body("a b", "SALES");
        send(post("/api/users"), token, badName).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists()); // order of errors is not guaranteed

        Map<String, Object> weak = body(newName("weak"), "SALES");
        weak.put("temporaryPassword", "short");
        send(post("/api/users"), token, weak).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("temporaryPassword"));

        send(post("/api/users"), token, body(newName("norole"), "NO_SUCH_ROLE")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roles"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value(containsString("NO_SUCH_ROLE")));

        send(post("/api/users"), token, body(newName("norole"))).andExpect(status().isBadRequest());

        Map<String, Object> badMail = body(newName("badmail"), "SALES");
        badMail.put("email", "not-an-email");
        send(post("/api/users"), token, badMail).andExpect(status().isBadRequest());

        Map<String, Object> unknownField = body(newName("extra"), "SALES");
        unknownField.put("active", false); // over-posting is rejected, not ignored
        send(post("/api/users"), token, unknownField).andExpect(status().isBadRequest());
    }

    @Test
    void createIsAuditedWithoutSecrets() throws Exception {
        String token = adminToken();
        String username = newName("audited");
        JsonNode created = createUser(token, username, "SALES");

        AuditCommand event = lastEvent("USER_CREATED");
        assertThat(event.entity()).isEqualTo("User");
        assertThat(event.entityId()).isEqualTo(created.get("id").asText());
        assertThat(event.oldValue()).isNull();
        assertThat(event.newValue()).containsEntry("username", username).containsEntry("roles", List.of("SALES"));
        assertThat(event.newValue().keySet()).noneMatch(k -> k.toLowerCase().contains("password") && !k.equals("mustChangePassword"));
        assertThat(event.newValue().toString()).doesNotContain("$2a$").doesNotContain(TEMP);
    }

    // ------------------------------------------------------------------------------------------- read / list

    @Test
    void getReturnsTheUserOr404() throws Exception {
        String token = adminToken();
        JsonNode created = createUser(token, newName("getme"), "SALES");
        send(get("/api/users/" + created.get("id").asLong()), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNumber()).andExpect(jsonPath("$.createdAt").isNotEmpty());
        send(get("/api/users/999999999"), token, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listSupportsSearchFiltersPagingAndSortingWithAWhitelist() throws Exception {
        String token = adminToken();
        String tag = "lst" + SEQ.incrementAndGet() + "x";
        for (int i = 0; i < 3; i++) {
            createUser(token, tag + "a" + i, "SALES");
        }
        createUser(token, tag + "z", "DISPATCH");
        long inactive = createUser(token, tag + "off", "SALES").get("id").asLong();
        send(delete("/api/users/" + inactive), token, null).andExpect(status().isNoContent());

        send(get("/api/users?q=" + tag), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.content[0].username").value(tag + "a0"));
        send(get("/api/users?q=" + tag + "&active=false"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/users?q=" + tag + "&active=true&role=DISPATCH"), token, null)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].roles[0]").value("DISPATCH"));
        send(get("/api/users?q=" + tag + "&size=2&page=1&sort=username,desc"), token, null)
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content.length()").value(2));
        // wildcard characters in the search text are literal
        send(get("/api/users?q=" + tag + "%25"), token, null).andExpect(jsonPath("$.totalElements").value(0));

        send(get("/api/users?sort=passwordHash,asc"), token, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
        send(get("/api/users?size=500"), token, null).andExpect(jsonPath("$.size").value(100));
        send(get("/api/users"), token, null).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    // ------------------------------------------------------------------------------------------- update

    @Test
    void updateChangesProfileFieldsAndChecksTheVersion() throws Exception {
        String token = adminToken();
        JsonNode created = createUser(token, newName("upd"), "SALES");
        long id = created.get("id").asLong();

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("fullName", "Renamed Person");
        update.put("email", "renamed" + id + "@example.com");
        update.put("phone", "99999");
        update.put("employeeCode", "EC" + id);
        update.put("version", created.get("version").asLong());
        send(put("/api/users/" + id), token, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Person")).andExpect(jsonPath("$.phone").value("99999"))
                .andExpect(jsonPath("$.username").value(created.get("username").asText()));

        AuditCommand event = lastEvent("USER_UPDATED");
        assertThat(event.oldValue()).containsEntry("fullName", "Full " + created.get("username").asText());
        assertThat(event.newValue()).containsEntry("fullName", "Renamed Person");

        // the same (now stale) version again
        send(put("/api/users/" + id), token, update).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void updateRejectsAnEmailBelongingToAnotherUser() throws Exception {
        String token = adminToken();
        JsonNode first = createUser(token, newName("em"), "SALES");
        JsonNode second = createUser(token, newName("em"), "SALES");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("fullName", "X");
        update.put("email", first.get("email").asText());
        update.put("version", second.get("version").asLong());
        send(put("/api/users/" + second.get("id").asLong()), token, update).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------------------------------- roles

    @Test
    void assigningRolesInvalidatesOldTokensAndTheRefreshedTokenHasTheNewPermissions() throws Exception {
        String token = adminToken();
        String username = newName("rolechg");
        long id = createUser(token, username, "OPERATOR").get("id").asLong();
        String changed = "My-Own-Password-9!";
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "currentPassword", TEMP, "newPassword", changed))));
        Api.Session session = Api.login(mockMvc, json, username, changed);
        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("PRODUCT_UPDATE"))));

        send(put("/api/users/" + id + "/roles"), token, Map.of("roles", List.of("ENGINEER"), "reason", "promoted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ENGINEER"));

        // the old token is refused straight away (cache evicted, permission_version bumped) ...
        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        // ... and a refreshed one carries the new permissions
        String refreshed = json.readTree(mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("ims_refresh", session.refreshToken()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(Api.bearer(get("/api/auth/me"), refreshed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ENGINEER")).andExpect(jsonPath("$.permissions", hasItem("PRODUCT_UPDATE")));

        AuditCommand event = lastEvent("USER_ROLES_CHANGED");
        assertThat(event.oldValue()).containsEntry("roles", List.of("OPERATOR"));
        assertThat(event.newValue()).containsEntry("roles", List.of("ENGINEER"));
        assertThat(event.reason()).isEqualTo("promoted");
    }

    @Test
    void assigningUnknownOrNoRolesIsRejected() throws Exception {
        String token = adminToken();
        long id = createUser(token, newName("rl"), "SALES").get("id").asLong();
        send(put("/api/users/" + id + "/roles"), token, Map.of("roles", List.of("NOPE"))).andExpect(status().isBadRequest());
        send(put("/api/users/" + id + "/roles"), token, Map.of("roles", List.of())).andExpect(status().isBadRequest());
        send(put("/api/users/999999999/roles"), token, Map.of("roles", List.of("SALES"))).andExpect(status().isNotFound());
    }

    private void makeTheOnlyActiveAdmin(User keep) {
        jdbc.update("""
                UPDATE users SET active = FALSE WHERE id <> ? AND id IN
                  (SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE r.code = 'ADMIN')""", keep.getId());
        userAccess.evictAll();
    }

    @Test
    void theLastActiveAdminCannotLoseTheAdminRoleOrBeDeactivated() throws Exception {
        User admin = testUsers.create("ADMIN");
        String token = Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
        makeTheOnlyActiveAdmin(admin);

        send(put("/api/users/" + admin.getId() + "/roles"), token, Map.of("roles", List.of("SALES")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        send(delete("/api/users/" + admin.getId()), token, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        // nothing changed
        assertThat(jdbc.queryForObject("SELECT active FROM users WHERE id = ?", Boolean.class, admin.getId())).isTrue();

        // with a second Admin the first may step down
        createUser(token, newName("admin2"), "ADMIN");
        send(put("/api/users/" + admin.getId() + "/roles"), token, Map.of("roles", List.of("SALES"))).andExpect(status().isOk());
    }

    @Test
    void anAdminHoldingOtherRolesToo_stillCountsAsAdminOnlyWhileHoldingAdmin() throws Exception {
        User admin = testUsers.create("ADMIN");
        String token = Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
        makeTheOnlyActiveAdmin(admin);
        // keeping ADMIN alongside another role is fine
        send(put("/api/users/" + admin.getId() + "/roles"), token, Map.of("roles", List.of("ADMIN", "SALES"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------- deactivate / activate

    @Test
    void deactivatingEndsSessionsImmediatelyAndNeverDeletes() throws Exception {
        String token = adminToken();
        String username = newName("leaver");
        long id = createUser(token, username, "SALES").get("id").asLong();
        String pw = "My-Own-Password-9!";
        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "currentPassword", TEMP, "newPassword", pw))));
        Api.Session session = Api.login(mockMvc, json, username, pw);
        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isOk());

        mockMvc.perform(Api.bearer(delete("/api/users/" + id).param("reason", "left the company"), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(Api.bearer(get("/api/auth/me"), session.accessToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("ims_refresh", session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", pw)))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, id)).isEqualTo(1); // never deleted
        send(get("/api/users/" + id), token, null).andExpect(jsonPath("$.active").value(false));

        AuditCommand event = lastEvent("USER_DEACTIVATED");
        assertThat(event.reason()).isEqualTo("left the company");
        assertThat(event.oldValue()).containsEntry("active", true);
        assertThat(event.newValue()).containsEntry("active", false);

        long before = countEvents("USER_DEACTIVATED");
        send(delete("/api/users/" + id), token, null).andExpect(status().isNoContent()); // idempotent
        assertThat(countEvents("USER_DEACTIVATED")).isEqualTo(before);

        // and they can be brought back
        send(post("/api/users/" + id + "/activate"), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        Api.login(mockMvc, json, username, pw);
    }

    // ------------------------------------------------------------------------------------------- reset password

    @Test
    void adminResetUnlocksEndsSessionsAndForcesAChange() throws Exception {
        String token = adminToken();
        User victim = testUsers.create("SALES");
        Api.Session session = Api.login(mockMvc, json, victim.getUsername(), TestUsers.PASSWORD);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", victim.getUsername(), "password", "Wrong-Pass-1234!"))));
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", victim.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isLocked());

        send(post("/api/users/" + victim.getId() + "/reset-password"), token,
                Map.of("temporaryPassword", "Reset-Pass-54321!", "reason", "forgot")).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("ims_refresh", session.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", victim.getUsername(), "password", "Reset-Pass-54321!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", victim.getUsername(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isUnauthorized());

        AuditCommand event = lastEvent("USER_PASSWORD_RESET");
        assertThat(event.reason()).isEqualTo("forgot");
        assertThat(event.newValue().toString()).doesNotContain("Reset-Pass").doesNotContain("$2a$");

        send(post("/api/users/" + victim.getId() + "/reset-password"), token, Map.of("temporaryPassword", "weak"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("temporaryPassword"));
    }

    // ------------------------------------------------------------------------------------------- me

    @Test
    void meReturnsTheSignedInProfileAndNeedsAToken() throws Exception {
        User user = testUsers.create("MAINTENANCE");
        String token = Api.login(mockMvc, json, user.getUsername(), TestUsers.PASSWORD).accessToken();

        mockMvc.perform(Api.bearer(get("/api/auth/me"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.getUsername()))
                .andExpect(jsonPath("$.roles[0]").value("MAINTENANCE"))
                .andExpect(jsonPath("$.permissions", hasItem("MACHINE_MANAGE")))
                .andExpect(jsonPath("$.primaryDashboard").value("MAINTENANCE"));
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}

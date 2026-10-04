package com.springmfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Role-based access through the real path: a database user holding exactly one seeded role logs in at
 * {@code /api/auth/login}, and the JWT filter resolves the permissions the role was granted by the Flyway seed.
 * Nothing is supplied by hand, so an endpoint guarded by a code no role holds fails here.
 *
 * <p>Write endpoints are probed with a non-existent id and no body: a permitted caller reaches the service
 * (404), a forbidden caller is stopped by method security (403).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAccessIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Rbac#Test-2026";
    private static final String MISSING = "999999";

    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    /** One probed endpoint: who must get in, and who must be stopped with 403. */
    private record Probe(HttpMethod method, String path, String body, Set<String> allowed, Set<String> forbidden) {
        static Probe of(HttpMethod method, String path, Set<String> allowed, Set<String> forbidden) {
            return new Probe(method, path, null, allowed, forbidden);
        }

        /** Endpoints that bind a validated body need one, or argument binding answers 400 before method security. */
        static Probe withBody(HttpMethod method, String path, String body, Set<String> allowed,
                Set<String> forbidden) {
            return new Probe(method, path, body, allowed, forbidden);
        }
    }

    private static final List<Probe> PROBES = List.of(
            // master data
            Probe.of(HttpMethod.GET, "/api/customers", Set.of("ADMIN", "SALES"),
                    Set.of("OPERATOR", "QUALITY_MANAGER", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/suppliers", Set.of("ADMIN", "PURCHASE_MANAGER"),
                    Set.of("SALES", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/materials",
                    Set.of("ADMIN", "ENGINEER", "STORE_MANAGER", "PURCHASE_MANAGER", "PRODUCTION_MANAGER",
                            "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/products",
                    Set.of("ADMIN", "ENGINEER", "SALES", "MANAGEMENT", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("OPERATOR", "STORE_OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/warehouses",
                    Set.of("ADMIN", "STORE_MANAGER", "STORE_OPERATOR", "MANAGEMENT"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.DELETE, "/api/materials/" + MISSING, Set.of("ADMIN"),
                    Set.of("ENGINEER", "STORE_MANAGER")),
            Probe.of(HttpMethod.DELETE, "/api/products/" + MISSING, Set.of("ADMIN", "ENGINEER"),
                    Set.of("SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/machines",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR", "MAINTENANCE"), Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.GET, "/api/operations", Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER"),
                    Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.DELETE, "/api/rejection-reasons/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("OPERATOR", "STORE_MANAGER")),
            // identity, settings, audit
            Probe.of(HttpMethod.GET, "/api/admin/users", Set.of("ADMIN", "MANAGEMENT"),
                    Set.of("ENGINEER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/roles", Set.of("ADMIN"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/settings", Set.of("ADMIN"), Set.of("MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/settings", Set.of("ADMIN"), Set.of("MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/audit-logs", Set.of("ADMIN", "MANAGEMENT"), Set.of("OPERATOR")),
            // alerts
            Probe.of(HttpMethod.GET, "/api/alerts",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER",
                            "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.PUT, "/api/alerts/" + MISSING + "/acknowledge", Set.of("ADMIN", "STORE_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR")),
            // goods receipt
            Probe.of(HttpMethod.GET, "/api/procurement/goods-receipts/" + MISSING,
                    Set.of("ADMIN", "STORE_OPERATOR", "PURCHASE_MANAGER"), Set.of("OPERATOR", "SALES")),
            // sales and dispatch
            Probe.of(HttpMethod.GET, "/api/sales/orders", Set.of("ADMIN", "SALES", "DISPATCH", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/sales/orders/" + MISSING + "/approve", Set.of("ADMIN"),
                    Set.of("SALES", "DISPATCH")),
            Probe.withBody(HttpMethod.POST, "/api/sales/orders/" + MISSING + "/cancel", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "SALES"), Set.of("DISPATCH", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/sales/dispatches", Set.of("ADMIN", "DISPATCH", "SALES", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            // maintenance
            Probe.of(HttpMethod.GET, "/api/maintenance/requests", Set.of("ADMIN", "MAINTENANCE", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/maintenance/pm-schedules/" + MISSING + "/generate-request",
                    Set.of("ADMIN", "MAINTENANCE"), Set.of("MANAGEMENT", "PURCHASE_MANAGER")),
            // advanced quality (R16): SPC, CAPA, complaints, first article inspection, complaint report
            Probe.of(HttpMethod.GET, "/api/spc/charts", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES", "ENGINEER")),
            Probe.of(HttpMethod.GET, "/api/spc/charts/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/spc/charts",
                    "{\"productId\":" + MISSING + ",\"parameterName\":\"probe\",\"chartType\":\"XBAR_R\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR", "ENGINEER")),
            Probe.withBody(HttpMethod.POST, "/api/spc/charts/" + MISSING + "/points", "{\"values\":[1]}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/spc/charts/" + MISSING + "/recalculate-limits",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/capa", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/capa/" + MISSING + "/close", "{\"verificationNotes\":\"probe\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/capa/from-rejection/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "SALES")),
            Probe.of(HttpMethod.GET, "/api/complaints", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("SALES", "DISPATCH", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/complaints/lookups/customers", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("SALES", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/complaints",
                    "{\"customerId\":" + MISSING + ",\"complaintDate\":\"2026-10-04\",\"category\":\"OTHER\","
                            + "\"description\":\"probe\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/fai", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/fai", "{\"productId\":" + MISSING + "}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/fai/" + MISSING + "/results",
                    "{\"values\":[{\"itemId\":1,\"actualValue\":1.5}]}", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/fai/" + MISSING + "/complete", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/reports/QUAL_COMPLAINTS",
                    Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"), Set.of("OPERATOR", "SALES")));

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private final Map<String, String> tokenByRole = new LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createOneUserPerRoleAndLogIn() throws Exception {
        // user administration records an audit row, which needs an authenticated actor
        var actor = new CachedUser(9001L, "rbac-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            for (String role : ROLES) {
                String username = username(role);
                userAdminService.create(new CreateUserRequest(username, null, "RBAC " + role,
                        username + "@rbac.test", null, PASSWORD, Set.of(role)));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        for (String role : ROLES) {
            MvcResult login = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn();
            assertThat(login.getResponse().getStatus()).as("login as %s", role).isEqualTo(200);
            tokenByRole.put(role, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
        }
    }

    @AfterAll
    void removeTheUsers() {
        String like = "rbac_%_" + runId;
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    private String username(String role) {
        return "rbac_" + role.toLowerCase() + "_" + runId;
    }

    private int status(String role, Probe probe) throws Exception {
        var req = request(probe.method(), probe.path()).header("Authorization", "Bearer " + tokenByRole.get(role));
        if (probe.body() != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(probe.body());
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    @TestFactory
    Stream<DynamicTest> seededRolesReachExactlyTheEndpointsTheyShould() {
        return PROBES.stream().flatMap(probe -> Stream.concat(
                probe.allowed().stream().map(role -> DynamicTest.dynamicTest(
                        role + " may " + probe.method() + " " + probe.path(), () -> {
                            int status = status(role, probe);
                            assertThat(status).as("%s %s as %s", probe.method(), probe.path(), role)
                                    .isNotIn(401, 403);
                        })),
                probe.forbidden().stream().map(role -> DynamicTest.dynamicTest(
                        role + " must not " + probe.method() + " " + probe.path(), () -> {
                            assertThat(status(role, probe)).as("%s %s as %s", probe.method(), probe.path(), role)
                                    .isEqualTo(403);
                        }))));
    }

    @TestFactory
    Stream<DynamicTest> everyEndpointRejectsAnonymousCallers() {
        return PROBES.stream().map(probe -> DynamicTest.dynamicTest(
                "anonymous " + probe.method() + " " + probe.path(), () -> {
                    int status = mockMvc.perform(request(probe.method(), probe.path())).andReturn().getResponse()
                            .getStatus();
                    assertThat(status).isEqualTo(401);
                }));
    }
}

package com.springmfg.ims.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * At least one active, unlocked user must always hold ADMIN. The test database is shared with other integration
 * tests, so every administrator that already exists is parked (deactivated) for the duration of each test and put
 * back afterwards; the tests then control exactly who counts. Real HTTP calls against PostgreSQL, not
 * {@code @Transactional}: the race test needs real concurrent transactions.
 */
class LastAdminGuardIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Guard#Admin-2026";
    private static final String MANAGER_ROLE = "LAG_MANAGER";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);
    private static final String ONLY_OPERATOR = "{\"roleCodes\":[\"OPERATOR\"]}";

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private List<Long> parkedAdmins = new ArrayList<>();

    @BeforeEach
    void parkExistingAdministratorsAndCreateTheManagerRole() {
        cleanUp();
        parkedAdmins = new ArrayList<>(jdbc.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles ur "
                + "ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE r.code = 'ADMIN' AND u.active = TRUE "
                + "AND u.username NOT LIKE 'lastadmin-%'", Long.class));
        parkedAdmins.forEach(id -> jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", id));

        jdbc.update("INSERT INTO roles (code, name) VALUES (?, ?)", MANAGER_ROLE, MANAGER_ROLE);
        for (String permission : List.of("USER_VIEW", "USER_DELETE", "ROLE_MANAGE")) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) "
                    + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?",
                    MANAGER_ROLE, permission);
        }
    }

    @AfterEach
    void cleanUp() {
        String like = "lastadmin-%";
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)",
                MANAGER_ROLE);
        jdbc.update("DELETE FROM roles WHERE code = ?", MANAGER_ROLE);
        parkedAdmins.forEach(id -> jdbc.update("UPDATE users SET active = TRUE WHERE id = ?", id));
        parkedAdmins = new ArrayList<>();
    }

    /** A user who has already passed the first-login change, so only the guard is under test. */
    private String newUser(String... roles) {
        String username = "lastadmin-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Guard Subject", username + "@lastadmin.test",
                null, PASSWORD, Set.of(roles)));
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);
        return username;
    }

    private long idOf(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private String tokenOf(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private int putRoles(String token, long targetId, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/users/" + targetId + "/roles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    private Set<String> rolesOf(String username) {
        return Set.copyOf(jdbc.queryForList("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                + "JOIN users u ON u.id = ur.user_id WHERE u.username = ?", String.class, username));
    }

    @Test
    void removingTheAdministratorRoleFromTheLastAdministratorIsRefused() throws Exception {
        String admin = newUser("ADMIN");
        String manager = tokenOf(newUser(MANAGER_ROLE));

        mockMvc.perform(put("/api/admin/users/" + idOf(admin) + "/roles").header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content(ONLY_OPERATOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        assertThat(rolesOf(admin)).containsExactly("ADMIN");
    }

    @Test
    void withASecondActiveAdministratorTheRoleCanBeRemoved() throws Exception {
        String first = newUser("ADMIN");
        newUser("ADMIN");
        String manager = tokenOf(newUser(MANAGER_ROLE));

        assertThat(putRoles(manager, idOf(first), ONLY_OPERATOR)).isEqualTo(200);

        assertThat(rolesOf(first)).containsExactly("OPERATOR");
    }

    @Test
    void anAdministratorWhoIsInactiveOrLockedDoesNotCountAsTheOtherOne() throws Exception {
        String first = newUser("ADMIN");
        String second = newUser("ADMIN");
        String manager = tokenOf(newUser(MANAGER_ROLE));

        jdbc.update("UPDATE users SET active = FALSE WHERE username = ?", second);
        assertThat(putRoles(manager, idOf(first), ONLY_OPERATOR)).as("the other administrator is inactive")
                .isEqualTo(400);

        jdbc.update("UPDATE users SET active = TRUE, locked_until = now() + interval '15 minutes' WHERE username = ?",
                second);
        assertThat(putRoles(manager, idOf(first), ONLY_OPERATOR)).as("the other administrator is locked")
                .isEqualTo(400);

        jdbc.update("UPDATE users SET locked_until = NULL, failed_attempts = 0 WHERE username = ?", second);
        assertThat(putRoles(manager, idOf(first), ONLY_OPERATOR)).as("the other administrator is usable")
                .isEqualTo(200);
    }

    @Test
    void deactivatingTheLastAdministratorIsRefusedEvenWhenTheyHoldAnotherRoleToo() throws Exception {
        // the old check only looked at accounts whose single role was ADMIN, so this one slipped through
        String admin = newUser("ADMIN", "OPERATOR");
        String manager = tokenOf(newUser(MANAGER_ROLE));

        mockMvc.perform(delete("/api/admin/users/" + idOf(admin)).header("Authorization", "Bearer " + manager))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        assertThat(jdbc.queryForObject("SELECT active FROM users WHERE username = ?", Boolean.class, admin)).isTrue();

        newUser("ADMIN");
        mockMvc.perform(delete("/api/admin/users/" + idOf(admin)).header("Authorization", "Bearer " + manager))
                .andExpect(status().isNoContent());
    }

    @Test
    void theLastAdministratorCannotDemoteThemselves() throws Exception {
        String admin = newUser("ADMIN");

        assertThat(putRoles(tokenOf(admin), idOf(admin), ONLY_OPERATOR)).isEqualTo(400);

        assertThat(rolesOf(admin)).containsExactly("ADMIN");
    }

    @Test
    void twoAdministratorsDemotingEachOtherAtTheSameTimeCannotBothSucceed() throws Exception {
        String a = newUser("ADMIN");
        String b = newUser("ADMIN");
        String tokenA = tokenOf(a);
        String tokenB = tokenOf(b);
        CyclicBarrier startTogether = new CyclicBarrier(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> aDemotesB = () -> {
                startTogether.await();
                return putRoles(tokenA, idOf(b), ONLY_OPERATOR);
            };
            Callable<Integer> bDemotesA = () -> {
                startTogether.await();
                return putRoles(tokenB, idOf(a), ONLY_OPERATOR);
            };
            Future<Integer> first = pool.submit(aDemotesB);
            Future<Integer> second = pool.submit(bDemotesA);
            List<Integer> statuses = List.of(first.get(), second.get());

            assertThat(statuses.stream().filter(s -> s == 200).count()).as("statuses %s", statuses).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }

        Long stillAdmins = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM users u JOIN user_roles ur "
                + "ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE r.code = 'ADMIN' AND u.active = TRUE "
                + "AND u.username LIKE 'lastadmin-%'", Long.class);
        assertThat(stillAdmins).as("someone can still administer").isEqualTo(1L);
    }
}

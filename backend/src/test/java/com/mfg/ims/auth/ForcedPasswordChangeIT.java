package com.mfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * A user whose password is still the temporary one set by an administrator may call only the password-change
 * allow-list; every other API call is refused by the server with 403 PASSWORD_CHANGE_REQUIRED. Real login and real
 * HTTP calls against PostgreSQL, not {@code @Transactional}: the flag and the cache eviction only matter once the
 * password change has committed.
 */
class ForcedPasswordChangeIT extends AbstractIntegrationTest {

    private static final String TEMP_PASSWORD = "Temp#Start-2026";
    private static final String NEW_PASSWORD = "Chosen#Pass-2027";
    private static final String COOKIE = "refresh_token";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private record Session(String accessToken, Cookie refreshCookie) {}

    /** An ADMIN, so that the call that is refused would otherwise be permitted: the only reason for the 403 is the flag. */
    private String newAdmin() {
        String username = "forcepw-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Forced Change", username + "@force.test", null,
                TEMP_PASSWORD, Set.of("ADMIN")));
        return username;
    }

    private Session login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session(JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken"),
                res.getResponse().getCookie(COOKIE));
    }

    private static String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    @AfterEach
    void cleanUp() {
        String like = "forcepw-%";
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    @Test
    void anAdminCreatedUserStartsWithTheFlagSet() throws Exception {
        String username = newAdmin();

        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE username = ?", Boolean.class,
                username)).isTrue();
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + TEMP_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat((Boolean) JsonPath.read(res.getResponse().getContentAsString(), "$.mustChangePassword")).isTrue();
    }

    @Test
    void aFlaggedUserIsRefusedOnEveryOrdinaryEndpointEvenWhenTheyHoldThePermission() throws Exception {
        Session s = login(newAdmin(), TEMP_PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(s)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", bearer(s)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void theAllowListStaysOpenWhileTheFlagIsSet() throws Exception {
        Session s = login(newAdmin(), TEMP_PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(s))).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(s.refreshCookie())).andExpect(status().isOk());
        mockMvc.perform(get("/api/system/ping").header("Authorization", bearer(s))).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").cookie(s.refreshCookie()).header("Authorization", bearer(s)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingThePasswordRestoresAccessAtOnceWithoutWaitingForTheCacheToExpire() throws Exception {
        Session s = login(newAdmin(), TEMP_PASSWORD);
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(s))).andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(s))
                        .cookie(s.refreshCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + TEMP_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isNoContent());

        // The old access token is stale (the permission version moved), so it is not a way around the flag...
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(s))).andExpect(status().isUnauthorized());

        // ...and the session that changed the password renews straight into a fully working one.
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(s.refreshCookie()))
                .andExpect(status().isOk()).andReturn();
        String fresh = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");
        assertThat((Boolean) JsonPath.read(refreshed.getResponse().getContentAsString(), "$.mustChangePassword"))
                .isFalse();
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk());
    }

    @Test
    void aUserWhoAlreadyChangedTheirPasswordIsNeverBlocked() throws Exception {
        String username = newAdmin();
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);

        Session s = login(username, TEMP_PASSWORD);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(s))).andExpect(status().isOk());
    }
}

package com.springmfg.ims.auth;

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
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * The browser keeps its session across reloads by posting the HttpOnly refresh cookie to {@code /api/auth/refresh}.
 * These tests drive that contract end to end against real PostgreSQL: login, rotation, reuse detection, logout with
 * only the cookie, the form-level error for a wrong current password, and what a password change does to the
 * user's other sessions. They are deliberately not {@code @Transactional}: the revocations under test only matter
 * once they are committed.
 */
class AuthSessionIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Sess#Start-2026";
    private static final String NEW_PASSWORD = "Sess#Change-2027";
    private static final String COOKIE = "refresh_token";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;

    private record Session(String accessToken, String refreshCookie, MvcResult result) {}

    private String newUser() {
        String username = "authsess-" + SEQ.incrementAndGet();
        userAdminService.create(new CreateUserRequest(username, null, "Session Tester", username + "@sess.test", null,
                PASSWORD, Set.of("OPERATOR")));
        return username;
    }

    private Session login(String username, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return session(res);
    }

    private static Session session(MvcResult res) throws Exception {
        Cookie cookie = res.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("the refresh token travels in a cookie").isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        String token = JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
        return new Session(token, cookie.getValue(), res);
    }

    private MvcResult refresh(String cookieValue, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookieValue)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    @AfterEach
    void cleanUp() {
        String like = "authsess-%";
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    @Test
    void refreshReturnsTheFullUserSoTheBrowserCanRestoreItsSession() throws Exception {
        String username = newUser();
        Session first = login(username, PASSWORD);

        MvcResult res = refresh(first.refreshCookie(), 200);
        String body = res.getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.user.username")).isEqualTo(username);
        assertThat((String) JsonPath.read(body, "$.user.fullName")).isEqualTo("Session Tester");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.user.roles")).containsExactly("OPERATOR");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.user.permissions")).isNotEmpty();
        assertThat((Boolean) JsonPath.read(body, "$.mustChangePassword")).isTrue();
    }

    @Test
    void refreshRotatesTheCookieAndAReusedTokenRevokesTheWholeFamily() throws Exception {
        String username = newUser();
        Session first = login(username, PASSWORD);

        Session second = session(refresh(first.refreshCookie(), 200));
        assertThat(second.refreshCookie()).isNotEqualTo(first.refreshCookie());

        // Once the grace window for concurrent requests has passed, presenting a rotated-away token is theft, so
        // the whole family dies, including the legitimate newest token. The old code threw after revoking, which
        // rolled the revocation back.
        ageRevocations(username, 60);
        refresh(first.refreshCookie(), 401);
        refresh(second.refreshCookie(), 401);
    }

    @Test
    void aTokenRotatedAMomentAgoIsAConcurrentRequestNotTheft() throws Exception {
        // D-20: two tabs, or a fast Back navigation, send the same cookie before the first response has replaced it.
        String username = newUser();
        Session first = login(username, PASSWORD);

        Session second = session(refresh(first.refreshCookie(), 200));
        Session third = session(refresh(first.refreshCookie(), 200));
        assertThat(third.refreshCookie()).isNotEqualTo(first.refreshCookie());
        assertThat(third.refreshCookie()).isNotEqualTo(second.refreshCookie());

        // whichever cookie the browser ended up with keeps working, and the session is not revoked
        refresh(second.refreshCookie(), 200);
        refresh(third.refreshCookie(), 200);

        // the grace window does not extend: replaying the first token after it has passed still kills the family
        ageRevocations(username, 60);
        refresh(first.refreshCookie(), 401);
    }

    @Test
    void aLoggedOutTokenGetsNoGraceEvenImmediately() throws Exception {
        Session s = login(newUser(), PASSWORD);
        Session rotated = session(refresh(s.refreshCookie(), 200));
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, rotated.refreshCookie())))
                .andExpect(status().isNoContent());

        // the successor was revoked by the logout, so the earlier token cannot slip through the grace window
        refresh(s.refreshCookie(), 401);
        refresh(rotated.refreshCookie(), 401);
    }

    /** Pretends the rotations happened {@code seconds} ago, so the reuse grace window has passed. */
    private void ageRevocations(String username, int seconds) {
        jdbc.update("""
                UPDATE refresh_tokens SET revoked_at = revoked_at - make_interval(secs => ?)
                 WHERE revoked_at IS NOT NULL
                   AND user_id = (SELECT id FROM users WHERE username = ?)
                """, seconds, username);
    }

    @Test
    void logoutNeedsOnlyTheRefreshCookieAndKillsIt() throws Exception {
        Session s = login(newUser(), PASSWORD);

        // No Authorization header: a tab whose access token has expired must still be able to sign out.
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, s.refreshCookie())))
                .andExpect(status().isNoContent());

        refresh(s.refreshCookie(), 401);
    }

    @Test
    void aWrongCurrentPasswordIsA400WithItsOwnCodeNotA401() throws Exception {
        Session s = login(newUser(), PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(s))
                        .cookie(new Cookie(COOKIE, s.refreshCookie())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Not#The-Password1\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

        // The session is untouched.
        refresh(s.refreshCookie(), 200);
    }

    @Test
    void changingThePasswordEndsOtherSessionsButKeepsTheOneThatChangedIt() throws Exception {
        String username = newUser();
        Session a = login(username, PASSWORD);
        Session b = login(username, PASSWORD);

        mockMvc.perform(post("/api/auth/change-password").header("Authorization", bearer(a))
                        .cookie(new Cookie(COOKIE, a.refreshCookie())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // The other device can neither refresh nor keep using the access token it already holds.
        refresh(b.refreshCookie(), 401);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(b))).andExpect(status().isUnauthorized());

        // The device that changed it recovers through its own refresh cookie, and the flag is cleared.
        MvcResult recovered = refresh(a.refreshCookie(), 200);
        assertThat((Boolean) JsonPath.read(recovered.getResponse().getContentAsString(), "$.mustChangePassword"))
                .isFalse();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(session(recovered))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        login(username, NEW_PASSWORD);
    }

    @Test
    void usersCreatedByAnAdministratorMustChangeTheirPasswordAtFirstLogin() throws Exception {
        Session s = login(newUser(), PASSWORD);

        assertThat((Boolean) JsonPath.read(s.result().getResponse().getContentAsString(), "$.mustChangePassword"))
                .isTrue();
    }

    @Test
    void repeatedWrongPasswordsLockTheAccount() throws Exception {
        String username = newUser();
        String wrong = "{\"username\":\"" + username + "\",\"password\":\"Wrong#Guess-1234\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        // Lockout is only real if the failed-attempt counter survives the exception that reports the failure.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().is(423));
    }
}

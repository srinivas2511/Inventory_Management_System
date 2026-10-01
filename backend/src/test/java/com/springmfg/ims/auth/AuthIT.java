package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestUsers;

/** Task 1.3: login, lockout, rotating refresh, logout, password policy/history, forgot/reset password. */
@RecordApplicationEvents
class AuthIT extends AbstractIntegrationTest {

    private static final Pattern REFRESH = Pattern.compile("ims_refresh=([^;]*)");
    private static final String NEW_PASSWORD = "Brand-New-Pass-77!";

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtService jwt;
    @Autowired
    ApplicationEvents events;

    // ------------------------------------------------------------------------------------------- helpers

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        var request = post("/api/auth/refresh");
        if (refreshToken != null) {
            request.cookie(new Cookie("ims_refresh", refreshToken));
        }
        return mockMvc.perform(request);
    }

    private static String refreshCookie(MvcResult result) {
        Matcher m = REFRESH.matcher(result.getResponse().getHeader("Set-Cookie"));
        assertThat(m.find()).as("Set-Cookie carries the refresh token").isTrue();
        return m.group(1);
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private int failedAttempts(User user) {
        return jdbc.queryForObject("SELECT failed_attempts FROM users WHERE id = ?", Integer.class, user.getId());
    }

    private long count(AuthEvent.Type type, String username) {
        return events.stream(AuthEvent.class).filter(e -> e.type() == type && username.equals(e.username())).count();
    }

    // ------------------------------------------------------------------------------------------- login

    @Test
    void loginReturnsTokenProfileAndSecureRefreshCookie() throws Exception {
        User user = testUsers.create("OPERATOR");

        MvcResult result = login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.expiresIn").value(org.hamcrest.Matchers.lessThanOrEqualTo(900)))
                .andExpect(jsonPath("$.user.username").value(user.getUsername()))
                .andExpect(jsonPath("$.user.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.user.permissions[0]").value("PROBLEM_REPORT")) // sorted alphabetically
                .andExpect(jsonPath("$.user.permissions[1]").value("PRODUCTION_EXECUTE"))
                .andExpect(jsonPath("$.user.primaryDashboard").value("OPERATOR"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth").contains("Max-Age=");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshCookie(result));

        String accessToken = json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        JwtService.AccessClaims claims = jwt.parse(accessToken);
        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.roles()).containsExactly("OPERATOR");
        assertThat(claims.permissionVersion()).isEqualTo(1);
        assertThat(count(AuthEvent.Type.LOGIN_SUCCESS, user.getUsername())).isEqualTo(1);
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        User user = testUsers.create("ADMIN");
        login(user.getUsername().toUpperCase(), TestUsers.PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.primaryDashboard").value("ADMIN"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericAnswer() throws Exception {
        User user = testUsers.create("SALES");
        String wrong = login(user.getUsername(), "Wrong-Pass-1234!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andReturn().getResponse().getContentAsString();
        String unknown = login("no-such-user", "Wrong-Pass-1234!").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrong).get("detail")).isEqualTo(json.readTree(unknown).get("detail"));
        assertThat(count(AuthEvent.Type.LOGIN_FAILURE, user.getUsername())).isEqualTo(1);
        assertThat(count(AuthEvent.Type.LOGIN_FAILURE, "no-such-user")).isEqualTo(1);
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        User user = testUsers.create("SALES");
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void malformedLoginRequestIsRejectedAsValidationFailure() throws Exception {
        postJson("/api/auth/login", Map.of("username", "", "password", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------------------------------- lockout

    @Test
    void fifthFailedLoginLocksTheAccountForFifteenMinutes() throws Exception {
        User user = testUsers.create("SALES");
        for (int i = 1; i <= 4; i++) {
            login(user.getUsername(), "Wrong-Pass-1234!").andExpect(status().isUnauthorized());
            assertThat(failedAttempts(user)).isEqualTo(i); // failures are persisted despite the error response
        }
        login(user.getUsername(), "Wrong-Pass-1234!").andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        Instant lockedUntil = jdbc.queryForObject("SELECT locked_until FROM users WHERE id = ?",
                java.sql.Timestamp.class, user.getId()).toInstant();
        assertThat(lockedUntil).isBetween(Instant.now().plus(14, ChronoUnit.MINUTES), Instant.now().plus(16, ChronoUnit.MINUTES));
        assertThat(count(AuthEvent.Type.ACCOUNT_LOCKED, user.getUsername())).isEqualTo(1);
    }

    @Test
    void lockedAccountRejectsEvenTheCorrectPasswordUntilTheLockExpires() throws Exception {
        User user = testUsers.create("SALES");
        for (int i = 0; i < 5; i++) {
            login(user.getUsername(), "Wrong-Pass-1234!");
        }
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        jdbc.update("UPDATE users SET locked_until = now() - interval '1 second' WHERE id = ?", user.getId());
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isOk());
        assertThat(failedAttempts(user)).isZero();
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCounter() throws Exception {
        User user = testUsers.create("SALES");
        for (int i = 0; i < 4; i++) {
            login(user.getUsername(), "Wrong-Pass-1234!");
        }
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isOk());
        assertThat(failedAttempts(user)).isZero();
        login(user.getUsername(), "Wrong-Pass-1234!").andExpect(status().isUnauthorized()); // not locked by old failures
    }

    // ------------------------------------------------------------------------------------------- forced change

    @Test
    void firstLoginRequiresAPasswordChangeAndOpensNoSession() throws Exception {
        User user = testUsers.create("OPERATOR", true);

        MvcResult result = login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(count(AuthEvent.Type.PASSWORD_CHANGE_REQUIRED, user.getUsername())).isEqualTo(1);
    }

    @Test
    void changingThePasswordSignsTheUserInAndClearsTheFlag() throws Exception {
        User user = testUsers.create("OPERATOR", true);

        MvcResult result = postJson("/api/auth/change-password", Map.of("username", user.getUsername(),
                "currentPassword", TestUsers.PASSWORD, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        assertThat(refreshCookie(result)).isNotBlank();

        login(user.getUsername(), NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());
        assertThat(count(AuthEvent.Type.PASSWORD_CHANGED, user.getUsername())).isEqualTo(1);
    }

    @Test
    void changePasswordNeedsTheCorrectCurrentPassword() throws Exception {
        User user = testUsers.create("OPERATOR", true);
        postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", "Wrong-Pass-1234!",
                "newPassword", NEW_PASSWORD)).andExpect(status().isUnauthorized());
        assertThat(failedAttempts(user)).isEqualTo(1); // guessing the current password counts towards lockout
    }

    @Test
    void passwordPolicyViolationsAreReportedPerField() throws Exception {
        User user = testUsers.create("OPERATOR", true);
        postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", TestUsers.PASSWORD,
                "newPassword", "short")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.fieldErrors[?(@.message=='Must be at least 12 characters long.')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.message=='Must contain an upper-case letter.')]").exists());
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(jsonPath("$.mustChangePassword").value(true)); // unchanged
    }

    @Test
    void passwordMustNotContainTheUsername() throws Exception {
        User user = testUsers.create("OPERATOR", true);
        postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", TestUsers.PASSWORD,
                "newPassword", "Xx-" + user.getUsername() + "-Yy-99!")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Must not contain the username."));
    }

    @Test
    void thePasswordCannotBeReusedFromTheLastFive() throws Exception {
        User user = testUsers.create("OPERATOR");
        String current = TestUsers.PASSWORD;
        // five changes: Pw-A ... Pw-E
        String[] chain = { "Pw-Alpha-Change-1!", "Pw-Bravo-Change-2!", "Pw-Charlie-Change-3!", "Pw-Delta-Change-4!",
                "Pw-Echo-Change-5!" };
        for (String next : chain) {
            postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", current,
                    "newPassword", next)).andExpect(status().isOk());
            current = next;
        }
        // the current password and the previous ones are refused ...
        for (String reused : new String[] { chain[4], chain[3], chain[2], chain[1] }) {
            postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", current,
                    "newPassword", reused)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].message").value(org.hamcrest.Matchers.containsString("Must differ")));
        }
        // ... while one that has dropped out of the last five is allowed again
        postJson("/api/auth/change-password", Map.of("username", user.getUsername(), "currentPassword", current,
                "newPassword", TestUsers.PASSWORD)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------- refresh / logout

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        User user = testUsers.create("OPERATOR");
        String raw = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());

        List<String> stored = jdbc.queryForList("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, user.getId());
        assertThat(stored).containsExactly(sha256(raw));
        assertThat(stored.get(0)).isNotEqualTo(raw);
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSameAbsoluteExpiry() throws Exception {
        User user = testUsers.create("OPERATOR");
        String first = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());

        MvcResult rotated = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(user.getUsername())).andReturn();
        String second = refreshCookie(rotated);
        assertThat(second).isNotEqualTo(first);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT revoked_at, replaced_by, expires_at, family_id FROM refresh_tokens WHERE user_id = ? ORDER BY id", user.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("revoked_at")).isNotNull();
        assertThat(rows.get(0).get("replaced_by")).isNotNull();
        assertThat(rows.get(1).get("revoked_at")).isNull();
        assertThat(rows.get(1).get("expires_at")).isEqualTo(rows.get(0).get("expires_at"));
        assertThat(rows.get(1).get("family_id")).isEqualTo(rows.get(0).get("family_id"));

        refresh(second).andExpect(status().isOk()); // the new token works
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        User user = testUsers.create("OPERATOR");
        String first = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        // an attacker (or a stale tab) replays the already-used first token
        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(count(AuthEvent.Type.TOKEN_REUSE_DETECTED, user.getUsername())).isEqualTo(1);

        // the legitimate newest token is now dead too: the user has to sign in again
        refresh(second).andExpect(status().isUnauthorized());
        Integer live = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, user.getId());
        assertThat(live).isZero();

        // other sessions of the same user are untouched
        String other = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        refresh(other).andExpect(status().isOk());
    }

    @Test
    void refreshFailsWithoutCookieWithUnknownTokenAndAfterExpiry() throws Exception {
        refresh(null).andExpect(status().isUnauthorized());
        refresh("not-a-real-token").andExpect(status().isUnauthorized());

        User user = testUsers.create("OPERATOR");
        String token = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", user.getId());
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        User user = testUsers.create("OPERATOR");
        String token = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReflectsCurrentPermissionVersion() throws Exception {
        User user = testUsers.create("OPERATOR");
        String token = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        jdbc.update("UPDATE users SET permission_version = permission_version + 1 WHERE id = ?", user.getId());

        String access = json.readTree(refresh(token).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString()).get("accessToken").asText();
        assertThat(jwt.parse(access).permissionVersion()).isEqualTo(2);
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        User user = testUsers.create("OPERATOR");
        String token = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("ims_refresh", token)))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("ims_refresh=;").contains("Max-Age=0");
        refresh(token).andExpect(status().isUnauthorized());
        assertThat(count(AuthEvent.Type.LOGOUT, user.getUsername())).isEqualTo(1);

        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent()); // idempotent, no cookie needed
    }

    // ------------------------------------------------------------------------------------------- forgot / reset

    private String requestResetToken(User user) throws Exception {
        events.clear();
        postJson("/api/auth/forgot-password", Map.of("email", user.getEmail())).andExpect(status().isAccepted());
        List<PasswordResetRequested> sent = events.stream(PasswordResetRequested.class).toList();
        assertThat(sent).hasSize(1);
        return sent.get(0).rawToken();
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForKnownAndUnknownAddresses() throws Exception {
        User user = testUsers.create("SALES");
        String known = postJson("/api/auth/forgot-password", Map.of("email", user.getEmail().toUpperCase()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        events.clear();
        String unknown = postJson("/api/auth/forgot-password", Map.of("email", "nobody@example.com"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);
        assertThat(events.stream(PasswordResetRequested.class)).isEmpty();
    }

    @Test
    void inactiveAccountsGetNoResetEmail() throws Exception {
        User user = testUsers.create("SALES");
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());
        events.clear();
        postJson("/api/auth/forgot-password", Map.of("email", user.getEmail())).andExpect(status().isAccepted());
        assertThat(events.stream(PasswordResetRequested.class)).isEmpty();
    }

    @Test
    void resetTokenIsStoredHashedAndWorksExactlyOnce() throws Exception {
        User user = testUsers.create("SALES");
        String token = requestResetToken(user);
        assertThat(jdbc.queryForList("SELECT token_hash FROM password_reset_tokens WHERE user_id = ?", String.class, user.getId()))
                .containsExactly(sha256(token));

        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD)).andExpect(status().isNoContent());
        login(user.getUsername(), NEW_PASSWORD).andExpect(status().isOk());
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isUnauthorized());

        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", "Another-Pass-88!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("token"));
        assertThat(count(AuthEvent.Type.PASSWORD_RESET_COMPLETED, user.getUsername())).isEqualTo(1);
    }

    @Test
    void aNewResetRequestInvalidatesTheEarlierLink() throws Exception {
        User user = testUsers.create("SALES");
        String first = requestResetToken(user);
        String second = requestResetToken(user);

        postJson("/api/auth/reset-password", Map.of("token", first, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", Map.of("token", second, "newPassword", NEW_PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void expiredOrUnknownResetTokensAreRejected() throws Exception {
        User user = testUsers.create("SALES");
        String token = requestResetToken(user);
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", user.getId());

        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"));
        postJson("/api/auth/reset-password", Map.of("token", "unknown-token", "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWeakNewPasswordLeavesTheResetLinkUsable() throws Exception {
        User user = testUsers.create("SALES");
        String token = requestResetToken(user);

        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", "weak")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD)).andExpect(status().isNoContent());
    }

    @Test
    void resettingThePasswordUnlocksTheAccountAndEndsOtherSessions() throws Exception {
        User user = testUsers.create("SALES");
        String session = refreshCookie(login(user.getUsername(), TestUsers.PASSWORD).andReturn());
        for (int i = 0; i < 5; i++) {
            login(user.getUsername(), "Wrong-Pass-1234!");
        }
        login(user.getUsername(), TestUsers.PASSWORD).andExpect(status().isLocked());

        String token = requestResetToken(user);
        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD)).andExpect(status().isNoContent());

        login(user.getUsername(), NEW_PASSWORD).andExpect(status().isOk());
        refresh(session).andExpect(status().isUnauthorized());
    }

    @Test
    void resetCannotReuseAPreviousPassword() throws Exception {
        User user = testUsers.create("SALES");
        String token = requestResetToken(user);
        postJson("/api/auth/reset-password", Map.of("token", token, "newPassword", TestUsers.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value(org.hamcrest.Matchers.containsString("Must differ")));
    }

    @Test
    void authEndpointsAreReachableWithoutATokenButOtherEndpointsAreNot() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}

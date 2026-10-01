package com.springmfg.ims.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserLockoutTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final Duration FIFTEEN = Duration.ofMinutes(15);

    private User user() {
        return new User("u", "U", "u@example.com", "hash");
    }

    @Test
    void lockingHappensOnTheFifthFailureNotBefore() {
        User user = user();
        for (int i = 1; i <= 4; i++) {
            assertThat(user.recordFailedLogin(NOW, 5, FIFTEEN)).isFalse();
            assertThat(user.isLocked(NOW)).isFalse();
            assertThat(user.getFailedAttempts()).isEqualTo(i);
        }
        assertThat(user.recordFailedLogin(NOW, 5, FIFTEEN)).isTrue();
        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(FIFTEEN));
        assertThat(user.getFailedAttempts()).isZero();
    }

    @Test
    void lockExpiresAfterFifteenMinutes() {
        User user = user();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, FIFTEEN);
        }
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(FIFTEEN))).isFalse();
    }

    @Test
    void successfulLoginClearsFailuresAndRecordsTime() {
        User user = user();
        user.recordFailedLogin(NOW, 5, FIFTEEN);
        user.recordSuccessfulLogin(NOW);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void changingPasswordClearsTheForcedChangeFlagAndAnyLock() {
        User user = user();
        assertThat(user.isMustChangePassword()).isTrue(); // new accounts must choose their own password
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, FIFTEEN);
        }
        user.changePassword("new-hash", NOW);
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
    }
}

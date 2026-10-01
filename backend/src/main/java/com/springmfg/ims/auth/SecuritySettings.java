package com.springmfg.ims.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runtime security policy from {@code system_settings} (DESIGN.md section 10.1). Read on use, so an Admin change
 * takes effect immediately. Falls back to the documented defaults if a row is missing or malformed.
 */
@Component
class SecuritySettings {

    static final int DEFAULT_MIN_PASSWORD_LENGTH = 12;
    static final int DEFAULT_LOCKOUT_ATTEMPTS = 5;
    static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private final JdbcTemplate jdbc;

    SecuritySettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    int minPasswordLength() {
        return intSetting("security.password.min_length", DEFAULT_MIN_PASSWORD_LENGTH);
    }

    int lockoutAttempts() {
        return intSetting("security.lockout.attempts", DEFAULT_LOCKOUT_ATTEMPTS);
    }

    Duration lockoutDuration() {
        return Duration.ofMinutes(intSetting("security.lockout.minutes", DEFAULT_LOCKOUT_MINUTES));
    }

    private int intSetting(String key, int fallback) {
        try {
            List<String> values = jdbc.queryForList("SELECT value FROM system_settings WHERE key = ?", String.class, key);
            if (values.isEmpty()) {
                return fallback;
            }
            int parsed = Integer.parseInt(values.get(0).trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException | DataAccessException e) {
            return fallback;
        }
    }
}

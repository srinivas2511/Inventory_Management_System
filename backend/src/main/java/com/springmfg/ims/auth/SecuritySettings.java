package com.springmfg.ims.auth;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.springmfg.ims.settings.SettingKey;
import com.springmfg.ims.settings.SystemSettingService;

/**
 * Runtime security policy (DESIGN.md section 10.1) read from the settings service, so an Admin change applies
 * on the next sign-in without a restart.
 */
@Component
class SecuritySettings {

    private final SystemSettingService settings;

    SecuritySettings(SystemSettingService settings) {
        this.settings = settings;
    }

    int minPasswordLength() {
        return settings.getInt(SettingKey.SECURITY_PASSWORD_MIN_LENGTH);
    }

    int lockoutAttempts() {
        return settings.getInt(SettingKey.SECURITY_LOCKOUT_ATTEMPTS);
    }

    Duration lockoutDuration() {
        return Duration.ofMinutes(settings.getInt(SettingKey.SECURITY_LOCKOUT_MINUTES));
    }
}

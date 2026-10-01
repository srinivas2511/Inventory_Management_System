package com.springmfg.ims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authentication settings (DESIGN.md section 10.2). The JWT secret has no default: the application refuses to
 * start without one of at least 32 characters (see {@code JwtService}).
 *
 * @param jwtSecret     HS256 signing secret, from the environment ({@code JWT_SECRET})
 * @param accessTtl     access-token lifetime
 * @param refreshTtl    absolute refresh-token lifetime, counted from login (rotation does not extend it)
 * @param resetTokenTtl password-reset link lifetime
 * @param accessCacheTtl how long a user's permissions are cached between requests; admin changes evict at once
 * @param cookieSecure  set the {@code Secure} flag on the refresh cookie (only false for plain-http development)
 * @param rateLimit     throttling of {@code /api/auth/**}
 */
@ConfigurationProperties(prefix = "ims.security")
public record ImsSecurityProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTtl,
        @DefaultValue("7d") Duration refreshTtl,
        @DefaultValue("30m") Duration resetTokenTtl,
        @DefaultValue("60s") Duration accessCacheTtl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue RateLimit rateLimit) {

    /**
     * @param authPerMinute  requests per client IP per minute for login, refresh, logout and change-password
     * @param resetPerWindow requests per client IP per {@code resetWindow} for forgot-password and reset-password
     */
    public record RateLimit(
            @DefaultValue("30") int authPerMinute,
            @DefaultValue("5") int resetPerWindow,
            @DefaultValue("15m") Duration resetWindow) {
    }
}

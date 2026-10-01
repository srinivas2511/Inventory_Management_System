package com.springmfg.ims.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Daily removal of expired refresh and reset tokens (DESIGN.md section 4.12). */
@Component
class TokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeJob.class);
    private static final Duration GRACE = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Clock clock;

    TokenPurgeJob(RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    void purgeExpired() {
        Instant cutoff = clock.instant().minus(GRACE);
        int refresh = refreshTokens.deleteExpiredBefore(cutoff);
        int reset = resetTokens.deleteExpiredBefore(cutoff);
        log.info("Token purge removed {} refresh and {} reset tokens", refresh, reset);
    }
}

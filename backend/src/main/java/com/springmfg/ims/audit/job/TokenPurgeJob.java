package com.springmfg.ims.audit.job;

import java.time.Instant;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.iam.repository.PasswordResetTokenRepository;
import com.springmfg.ims.iam.repository.RefreshTokenRepository;

@Component
public class TokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeJob.class);

    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordResetTokenRepository resetTokenRepo;

    public TokenPurgeJob(RefreshTokenRepository refreshTokenRepo,
                         PasswordResetTokenRepository resetTokenRepo) {
        this.refreshTokenRepo = refreshTokenRepo;
        this.resetTokenRepo = resetTokenRepo;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    @SchedulerLock(name = "token_purge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        refreshTokenRepo.deleteExpiredBefore(now);
        resetTokenRepo.deleteExpiredBefore(now);
        log.info("Token purge completed at {}", now);
    }
}

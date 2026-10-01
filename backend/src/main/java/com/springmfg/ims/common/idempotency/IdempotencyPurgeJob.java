package com.springmfg.ims.common.idempotency;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly removal of idempotency keys older than 48 hours (DESIGN.md section 4.12). */
@Component
public class IdempotencyPurgeJob {

    static final Duration RETENTION = Duration.ofHours(48);
    private static final Logger log = LoggerFactory.getLogger(IdempotencyPurgeJob.class);

    private final IdempotencyStore store;

    IdempotencyPurgeJob(IdempotencyStore store) {
        this.store = store;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void purge() {
        int removed = store.purgeOlderThan(RETENTION);
        if (removed > 0) {
            log.info("Idempotency purge removed {} keys", removed);
        }
    }
}

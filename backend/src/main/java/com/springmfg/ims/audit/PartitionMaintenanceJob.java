package com.springmfg.ims.audit;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the monthly {@code audit_logs} partitions ahead of the calendar (DESIGN.md section 4.12). The migration
 * creates the current and next 12 months; this job (monthly, and once at startup to cover any downtime) tops that
 * up using the idempotent database function {@code ensure_audit_partition}. Rows dated beyond the last partition
 * still land in the default partition, so a late run loses nothing.
 */
@Component
@Order(5)
public class PartitionMaintenanceJob implements ApplicationRunner {

    static final int MONTHS_AHEAD = 12;
    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    PartitionMaintenanceJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePartitions();
    }

    @Scheduled(cron = "0 15 2 1 * *")
    public void ensurePartitions() {
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i <= MONTHS_AHEAD; i++) {
            LocalDate month = today.plusMonths(i);
            try {
                jdbc.queryForObject("SELECT ensure_audit_partition(?)", String.class, java.sql.Date.valueOf(month));
            } catch (RuntimeException e) {
                // e.g. rows for that month already sit in the default partition; needs a DBA, must not stop the app
                log.error("Could not create the audit partition for {}: {}", month.withDayOfMonth(1), e.getMessage());
            }
        }
        log.info("Audit partitions ensured through {}", today.plusMonths(MONTHS_AHEAD).withDayOfMonth(1));
    }
}

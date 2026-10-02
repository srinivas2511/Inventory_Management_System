package com.springmfg.ims.audit.job;

import java.time.YearMonth;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);

    private final JdbcTemplate jdbc;

    public PartitionMaintenanceJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs on the 1st of each month at 01:00 UTC, creates partition 2 months ahead. */
    @Scheduled(cron = "0 0 1 1 * *", zone = "UTC")
    @SchedulerLock(name = "partition_maintenance", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    public void createFuturePartitions() {
        YearMonth target = YearMonth.now().plusMonths(2);
        String tableName = "ims.audit_logs_" + String.format("%d_%02d", target.getYear(), target.getMonthValue());
        String from = target.atDay(1).toString();
        String to = target.plusMonths(1).atDay(1).toString();

        String sql = "CREATE TABLE IF NOT EXISTS " + tableName +
            " PARTITION OF ims.audit_logs FOR VALUES FROM ('" + from + "') TO ('" + to + "')";
        jdbc.execute(sql);
        log.info("Ensured audit log partition: {}", tableName);
    }
}

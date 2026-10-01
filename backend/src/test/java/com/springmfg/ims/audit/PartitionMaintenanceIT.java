package com.springmfg.ims.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springmfg.ims.support.AbstractIntegrationTest;

/** Task 1.6: monthly partitions of audit_logs stay 12 months ahead and rows land in the right one. */
class PartitionMaintenanceIT extends AbstractIntegrationTest {

    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

    @Autowired
    PartitionMaintenanceJob job;
    @Autowired
    JdbcTemplate jdbc;

    private static String partition(LocalDate month) {
        return "audit_logs_" + month.format(SUFFIX);
    }

    private boolean exists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(current_schema() || '.' || ?) IS NOT NULL", Boolean.class, table));
    }

    private String partitionOf(String entityId) {
        return jdbc.queryForObject("SELECT tableoid::regclass::text FROM audit_logs WHERE entity = 'PartitionTest' AND entity_id = ?",
                String.class, entityId);
    }

    @Test
    void currentAndTwelveFutureMonthsHaveAPartition() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i <= PartitionMaintenanceJob.MONTHS_AHEAD; i++) {
            assertThat(exists(partition(today.plusMonths(i)))).as("month +%d", i).isTrue();
        }
    }

    @Test
    void theJobRecreatesAMissingFuturePartitionAndIsIdempotent() {
        LocalDate last = LocalDate.now().plusMonths(PartitionMaintenanceJob.MONTHS_AHEAD);
        jdbc.execute("DROP TABLE " + partition(last));
        assertThat(exists(partition(last))).isFalse();

        job.ensurePartitions();
        assertThat(exists(partition(last))).isTrue();

        Integer before = jdbc.queryForObject("SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'audit_logs'", Integer.class);
        job.ensurePartitions(); // second run changes nothing and does not fail
        Integer after = jdbc.queryForObject("SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'audit_logs'", Integer.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void rowsLandInTheirMonthsPartitionAndOnlyFarFutureRowsInTheDefault() {
        LocalDate sixMonthsOn = LocalDate.now().plusMonths(6).withDayOfMonth(15);
        jdbc.update("INSERT INTO audit_logs (occurred_at, action, entity, entity_id) VALUES (?::timestamptz, 'P', 'PartitionTest', 'near')",
                sixMonthsOn + "T12:00:00Z");
        jdbc.update("INSERT INTO audit_logs (occurred_at, action, entity, entity_id) VALUES (?::timestamptz, 'P', 'PartitionTest', 'far')",
                LocalDate.now().plusYears(6).withMonth(6).withDayOfMonth(15) + "T12:00:00Z");

        assertThat(partitionOf("near")).endsWith(partition(sixMonthsOn));
        assertThat(partitionOf("far")).endsWith("audit_logs_default");
        List<String> names = jdbc.queryForList("SELECT relname FROM pg_class WHERE relname LIKE 'audit_logs_%' AND relkind = 'r'", String.class);
        assertThat(names).contains("audit_logs_default");
    }
}

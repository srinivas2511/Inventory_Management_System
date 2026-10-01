package com.springmfg.ims.support;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditChange;
import com.springmfg.ims.audit.Audited;
import com.springmfg.ims.audit.Auditable;

/**
 * Test-only service exercising {@link Audited}. Each method makes a business change (a {@code number_sequences}
 * row) and then audits it, so tests can check that the two commit or roll back together.
 */
@Service
public class AuditProbeService {

    public record Result(String marker, Object newValue) implements Auditable {
        @Override
        public AuditChange auditChange() {
            return new AuditChange(marker, null, Map.of("prefix", marker, "value", newValue), "probe");
        }
    }

    private final JdbcTemplate jdbc;

    public AuditProbeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void businessChange(String marker) {
        jdbc.update("INSERT INTO number_sequences (prefix, seq_year, last_value) VALUES (?, 2026, 1)", marker);
    }

    @Transactional
    @Audited(action = "PROBE_OK", entity = "Probe")
    public Result ok(String marker) {
        businessChange(marker);
        return new Result(marker, 1);
    }

    /** The audit write itself fails: the snapshot cannot be serialised. */
    @Transactional
    @Audited(action = "PROBE_BAD_SNAPSHOT", entity = "Probe")
    public Result auditCannotBeWritten(String marker) {
        businessChange(marker);
        return new Result(marker, new Object());
    }

    /** A developer error: annotated but not returning an Auditable. */
    @Transactional
    @Audited(action = "PROBE_WRONG_TYPE", entity = "Probe")
    public String notAuditable(String marker) {
        businessChange(marker);
        return marker;
    }
}

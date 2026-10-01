package com.springmfg.ims.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.AuditProbeService;
import com.springmfg.ims.support.TestUsers;

/** Task 1.6: AuditService, the @Audited aspect and the same-transaction guarantee. */
class AuditServiceIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    AuditService audit;
    @Autowired
    AuditProbeService probe;
    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ObjectMapper json;

    private static String marker() {
        return "PROBE" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000;
    }

    private int auditRows(String entity, String entityId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity = ? AND entity_id = ?", Integer.class, entity, entityId);
    }

    private int sequenceRows(String marker) {
        return jdbc.queryForObject("SELECT count(*) FROM number_sequences WHERE prefix = ?", Integer.class, marker);
    }

    // ------------------------------------------------------------------------------------------- who / where

    @Test
    void recordsTheSignedInUserTheirRolesTheIpAndTheCorrelationId() throws Exception {
        User admin = testUsers.create("ADMIN");
        String token = Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
        String username = "audit.actor" + SEQ.incrementAndGet();

        mockMvc.perform(Api.bearer(Api.jsonBody(post("/api/users"), json, Map.of("username", username, "fullName", "A",
                "email", username + "@example.com", "roles", List.of("SALES"), "temporaryPassword", "Temp-Pass-12345!"))
                .header("X-Correlation-Id", "corr-abc-123456"), token)).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT a.* FROM audit_logs a WHERE a.action = 'USER_CREATED' AND a.new_value ->> 'username' = ?""", username);
        assertThat(row.get("user_id")).isEqualTo(admin.getId());
        assertThat(row.get("username")).isEqualTo(admin.getUsername());
        assertThat(row.get("roles")).isEqualTo("ADMIN");
        assertThat(row.get("ip_address")).isEqualTo("127.0.0.1");
        assertThat(row.get("correlation_id")).isEqualTo("corr-abc-123456");
        assertThat(row.get("entity")).isEqualTo("User");
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(row.get("old_value")).isNull();
    }

    @Test
    void workWithNoSignedInUserIsRecordedAsSystem() {
        // Spring's test framework leaves a mock request in the context; startup loaders and jobs have none
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        String id = marker();
        audit.record(new AuditCommand("SYSTEM_TASK", "Probe", id, null, Map.of("k", "v"), null));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_logs WHERE entity = 'Probe' AND entity_id = ?", id);
        assertThat(row.get("username")).isEqualTo("system");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("ip_address")).isNull();
    }

    // ------------------------------------------------------------------------------------------- content

    @Test
    void storesOldAndNewValuesAsJsonbAndMasksSecrets() {
        String id = marker();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("quantity", 500);
        before.put("passwordHash", "$2a$12$secret");
        Map<String, Object> after = Map.of("quantity", 480, "note", "Physical stock verification");
        audit.record(new AuditCommand("STOCK_ADJUSTMENT", "Probe", id, before, after, "Physical stock verification"));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT old_value ->> 'quantity' AS old_q, new_value ->> 'quantity' AS new_q, old_value ->> 'passwordHash' AS hash,
                       reason FROM audit_logs WHERE entity = 'Probe' AND entity_id = ?""", id);
        assertThat(row.get("old_q")).isEqualTo("500");
        assertThat(row.get("new_q")).isEqualTo("480");
        assertThat(row.get("hash")).isEqualTo("***");
        assertThat(row.get("reason")).isEqualTo("Physical stock verification");
    }

    @Test
    void overlongTextIsTruncatedRatherThanFailingTheBusinessOperation() {
        String id = marker();
        audit.record(new AuditCommand("LONG", "Probe", id, null, null, "r".repeat(900)));
        assertThat(jdbc.queryForObject("SELECT length(reason) FROM audit_logs WHERE entity = 'Probe' AND entity_id = ?",
                Integer.class, id)).isEqualTo(500);
    }

    // ------------------------------------------------------------------------------------------- @Audited and atomicity

    @Test
    void auditedMethodWritesItsAuditRowWithTheBusinessChange() {
        String m = marker();
        probe.ok(m);
        assertThat(sequenceRows(m)).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_logs WHERE action = 'PROBE_OK' AND entity_id = ?", m);
        assertThat(row.get("entity")).isEqualTo("Probe");
        assertThat(row.get("reason")).isEqualTo("probe");
    }

    @Test
    void ifTheAuditRowCannotBeWrittenTheBusinessChangeRollsBack() {
        String m = marker();
        assertThatThrownBy(() -> probe.auditCannotBeWritten(m)).isInstanceOf(RuntimeException.class);
        assertThat(sequenceRows(m)).as("business change rolled back").isZero();
        assertThat(auditRows("Probe", m)).isZero();
    }

    @Test
    void anAuditedMethodThatReturnsTheWrongTypeFailsLoudlyAndRollsBack() {
        String m = marker();
        assertThatThrownBy(() -> probe.notAuditable(m)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Auditable");
        assertThat(sequenceRows(m)).isZero();
    }

    @Test
    void auditRowAndBusinessChangeJoinAnOuterTransactionAndRollBackTogether() {
        String m = marker();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            probe.ok(m);
            assertThat(sequenceRows(m)).isEqualTo(1); // visible inside the transaction
            throw new IllegalStateException("something later in the same transaction fails");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sequenceRows(m)).isZero();
        assertThat(auditRows("Probe", m)).isZero();

        tx.executeWithoutResult(status -> probe.ok(m)); // and both survive a commit
        assertThat(sequenceRows(m)).isEqualTo(1);
        assertThat(auditRows("Probe", m)).isEqualTo(1);
    }

    @Test
    void auditRowsCannotBeChangedOrRemovedByTheApplication() {
        String id = marker();
        audit.record(new AuditCommand("IMMUTABLE", "Probe", id, null, null, null));
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'X' WHERE entity_id = ?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE entity_id = ?", id))
                .hasMessageContaining("append-only");
    }
}

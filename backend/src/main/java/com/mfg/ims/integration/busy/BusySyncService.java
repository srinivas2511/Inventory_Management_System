package com.mfg.ims.integration.busy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busy accounting full-sync service (16.3).
 * Reads POSTED journal_headers since last sync, builds Busy-compatible XML,
 * stages records in busy_sync_queue and updates busy.last_sync_at on success.
 */
@Service
public class BusySyncService {

    private static final Logger log = LoggerFactory.getLogger(BusySyncService.class);

    private final NamedParameterJdbcTemplate jdbc;

    public BusySyncService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SyncResult(int synced, int failed, Instant syncedAt) {}

    @Transactional
    public SyncResult sync() {
        String lastSyncAt = setting("busy.last_sync_at");
        Instant since = parseInstant(lastSyncAt);
        String voucherType = setting("busy.voucher_type");
        if (voucherType == null || voucherType.isBlank()) voucherType = "JV";

        // Find POSTED journals since last sync that are not yet in busy_sync_queue
        List<Map<String, Object>> journals = jdbc.queryForList("""
                SELECT jh.id, jh.journal_number, jh.posting_date, jh.description
                  FROM ims.journal_headers jh
                 WHERE jh.status = 'POSTED'
                   AND jh.created_at >= :since
                   AND NOT EXISTS (
                       SELECT 1 FROM ims.busy_sync_queue bq WHERE bq.journal_id = jh.id
                   )
                 ORDER BY jh.posting_date, jh.id
                """, new MapSqlParameterSource("since", java.sql.Timestamp.from(since)));

        int synced = 0;
        int failed = 0;
        Instant syncTime = Instant.now();

        for (Map<String, Object> journal : journals) {
            Long journalId = ((Number) journal.get("id")).longValue();
            String journalNumber = String.valueOf(journal.get("journal_number"));
            try {
                String xml = buildBusyXml(journalId, journalNumber, voucherType);
                // Insert into busy_sync_queue as EXPORTED
                jdbc.update("""
                        INSERT INTO ims.busy_sync_queue
                            (journal_id, journal_number, status, export_attempt, exported_at,
                             export_payload)
                        VALUES (:jid, :jnum, 'EXPORTED', 1, :now, CAST(:payload AS jsonb))
                        """,
                        new MapSqlParameterSource()
                                .addValue("jid", journalId)
                                .addValue("jnum", journalNumber)
                                .addValue("now", java.sql.Timestamp.from(syncTime))
                                .addValue("payload", "{\"xml\":" + jsonString(xml) + "}"));
                synced++;
            } catch (Exception e) {
                // Stage as FAILED
                jdbc.update("""
                        INSERT INTO ims.busy_sync_queue
                            (journal_id, journal_number, status, export_attempt, last_error)
                        VALUES (:jid, :jnum, 'FAILED', 1, :err)
                        """,
                        new MapSqlParameterSource()
                                .addValue("jid", journalId)
                                .addValue("jnum", journalNumber)
                                .addValue("err", e.getMessage()));
                failed++;
                log.warn("BusySync: failed journal {}: {}", journalNumber, e.getMessage());
            }
        }

        // Update last_sync_at on success (even partial sync moves the marker)
        if (synced > 0 || journals.isEmpty()) {
            jdbc.update("""
                    UPDATE ims.system_settings SET value = :ts WHERE key = 'busy.last_sync_at'
                    """, new MapSqlParameterSource("ts", syncTime.toString()));
        }

        log.info("BusySync: synced={}, failed={}", synced, failed);
        return new SyncResult(synced, failed, syncTime);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> syncLog() {
        return jdbc.queryForList("""
                SELECT id, journal_id, journal_number, status, export_attempt,
                       last_error, exported_at, created_at
                  FROM ims.busy_sync_queue
                 ORDER BY created_at DESC
                 LIMIT 50
                """, new MapSqlParameterSource());
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private String buildBusyXml(Long journalId, String journalNumber, String voucherType) {
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT ga.account_code, ga.name,
                       jl.debit_amount, jl.credit_amount,
                       jh.description, jh.posting_date
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
                 WHERE jh.id = :jid
                 ORDER BY jl.id
                """, new MapSqlParameterSource("jid", journalId));

        StringBuilder xml = new StringBuilder();
        xml.append("<VOUCHER>")
                .append("<VCHTYPE>").append(escape(voucherType)).append("</VCHTYPE>")
                .append("<VCHNO>").append(escape(journalNumber)).append("</VCHNO>");
        for (Map<String, Object> line : lines) {
            xml.append("<ENTRY>")
                    .append("<ACCOUNT>").append(escape(String.valueOf(line.getOrDefault("account_code", "")))).append("</ACCOUNT>")
                    .append("<ACCOUNTNAME>").append(escape(String.valueOf(line.getOrDefault("name", "")))).append("</ACCOUNTNAME>")
                    .append("<DEBIT>").append(line.getOrDefault("debit_amount", "0")).append("</DEBIT>")
                    .append("<CREDIT>").append(line.getOrDefault("credit_amount", "0")).append("</CREDIT>")
                    .append("<NARRATION>").append(escape(String.valueOf(line.getOrDefault("description", "")))).append("</NARRATION>")
                    .append("</ENTRY>");
        }
        xml.append("</VOUCHER>");
        return xml.toString();
    }

    private String setting(String key) {
        try {
            return jdbc.queryForObject(
                    "SELECT value FROM ims.system_settings WHERE key = :k",
                    new MapSqlParameterSource("k", key), String.class);
        } catch (Exception e) {
            return "";
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}

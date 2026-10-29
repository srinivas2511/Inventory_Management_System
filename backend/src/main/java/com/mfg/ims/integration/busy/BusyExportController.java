package com.mfg.ims.integration.busy;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busy accounting export stub.
 * Stages posted GL journal lines into busy_sync_queue.
 * Actual export writes a CSV to the configured export_path; in stub mode it only
 * marks records EXPORTED without writing any file.
 */
@RestController
@RequestMapping("/api/integration/busy-export")
public class BusyExportController {

    private static final Logger log = LoggerFactory.getLogger(BusyExportController.class);

    private final NamedParameterJdbcTemplate jdbc;

    public BusyExportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record QueueRow(
            Long id, Long journalId, String journalNumber, String status,
            int exportAttempt, String lastError, Instant exportedAt, Instant createdAt) {}

    public record ExportResult(int queued, int exported, int failed) {}

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('BUSY_EXPORT_VIEW')")
    public List<QueueRow> queue() {
        return jdbc.query(
                "SELECT * FROM ims.busy_sync_queue ORDER BY created_at DESC LIMIT 500",
                new MapSqlParameterSource(),
                (rs, i) -> new QueueRow(
                        rs.getLong("id"),
                        rs.getLong("journal_id"),
                        rs.getString("journal_number"),
                        rs.getString("status"),
                        rs.getInt("export_attempt"),
                        rs.getString("last_error"),
                        rs.getTimestamp("exported_at") != null ? rs.getTimestamp("exported_at").toInstant() : null,
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
                ));
    }

    @PostMapping("/stage")
    @PreAuthorize("hasAuthority('BUSY_EXPORT_RUN')")
    public ExportResult stage() {
        // Stage all POSTED journals not yet in the queue
        int queued = jdbc.update("""
                INSERT INTO ims.busy_sync_queue (journal_id, journal_number, status)
                SELECT jh.id, jh.journal_number, 'PENDING'
                FROM ims.journal_headers jh
                WHERE jh.status = 'POSTED'
                  AND NOT EXISTS (
                      SELECT 1 FROM ims.busy_sync_queue bq WHERE bq.journal_id = jh.id
                  )
                """, new MapSqlParameterSource());
        log.info("Busy export: staged {} journals", queued);
        return new ExportResult(queued, 0, 0);
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('BUSY_EXPORT_RUN')")
    public ExportResult run() {
        String exportPath = setting("busy.export_path");
        boolean enabled = Boolean.parseBoolean(setting("busy.enabled"));
        String voucherType = setting("busy.voucher_type");
        if (voucherType == null || voucherType.isBlank()) voucherType = "JV";

        List<Map<String, Object>> pending = jdbc.queryForList(
                "SELECT id, journal_id, journal_number FROM ims.busy_sync_queue WHERE status = 'PENDING'",
                new MapSqlParameterSource());

        int exported = 0;
        int failed = 0;

        for (Map<String, Object> row : pending) {
            Long queueId = ((Number) row.get("id")).longValue();
            Long journalId = ((Number) row.get("journal_id")).longValue();
            String journalNumber = String.valueOf(row.get("journal_number"));

            try {
                String csv = buildCsv(journalId, journalNumber, voucherType);
                if (enabled && exportPath != null && !exportPath.isBlank()) {
                    writeFile(exportPath, journalNumber + ".csv", csv);
                }
                jdbc.update("""
                        UPDATE ims.busy_sync_queue
                        SET status = 'EXPORTED', exported_at = :now,
                            export_attempt = export_attempt + 1, export_payload = :csv::jsonb
                        WHERE id = :id
                        """,
                        new MapSqlParameterSource()
                                .addValue("now", Instant.now())
                                .addValue("csv", "{\"csv\":\"" + csv.replace("\"", "\\\"") + "\"}")
                                .addValue("id", queueId));
                exported++;
            } catch (Exception e) {
                jdbc.update("""
                        UPDATE ims.busy_sync_queue
                        SET status = 'FAILED', export_attempt = export_attempt + 1,
                            last_error = :err
                        WHERE id = :id
                        """,
                        new MapSqlParameterSource()
                                .addValue("err", e.getMessage())
                                .addValue("id", queueId));
                failed++;
                log.warn("Busy export failed for journal {}: {}", journalNumber, e.getMessage());
            }
        }

        return new ExportResult(0, exported, failed);
    }

    private String buildCsv(Long journalId, String journalNumber, String voucherType) {
        List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT ga.account_code, jl.debit_amount, jl.credit_amount,
                       jh.description, jh.posting_date
                FROM ims.journal_lines jl
                JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
                WHERE jh.id = :jid
                ORDER BY jl.id
                """, new MapSqlParameterSource("jid", journalId));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("VoucherType,Date,AccountCode,Debit,Credit,Narration,Reference");
        for (Map<String, Object> line : lines) {
            pw.printf("%s,%s,%s,%s,%s,%s,%s%n",
                    voucherType,
                    line.getOrDefault("posting_date", ""),
                    line.getOrDefault("account_code", ""),
                    line.getOrDefault("debit_amount", "0"),
                    line.getOrDefault("credit_amount", "0"),
                    line.getOrDefault("description", "").toString().replace(",", ";"),
                    journalNumber);
        }
        return sw.toString();
    }

    private void writeFile(String dir, String filename, String content) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get(dir, filename);
        java.nio.file.Files.writeString(path, content);
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
}

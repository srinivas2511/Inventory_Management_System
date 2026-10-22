package com.mfg.ims.integration.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.search.QueryBounds;
import com.mfg.ims.common.settings.SystemSettingService;

/**
 * Full-entity export for data warehousing (INT-004). Only the curated {@code reporting.*} views are exported, so no
 * credentials, pay or bank data can leave; the table name and columns are fixed code, never request input. Rows are
 * read in id order a thousand at a time (keyset paging), so memory stays flat however large the entity is. CSV cells
 * that a spreadsheet would run as a formula are prefixed with an apostrophe.
 */
@Service
public class ExportService {

    static final int BATCH = 1000;
    static final String FINANCE_EXTRA = "ANALYTICS_FINANCE";

    /** {@code dateColumn} may be null; {@code extraAuthority} is needed on top of DATA_EXPORT when not null. */
    public record ExportSpec(String entity, String view, String dateColumn, String extraAuthority) {
    }

    private final Map<String, ExportSpec> specs = new LinkedHashMap<>();
    private final NamedParameterJdbcTemplate jdbc;
    private final SystemSettingService settings;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ExportService(NamedParameterJdbcTemplate jdbc, SystemSettingService settings, AuditService audit,
                         ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.audit = audit;
        this.mapper = mapper;
        add("materials", null, null);
        add("products", null, null);
        add("customers", null, null);
        add("suppliers", null, null);
        add("inventory_balances", null, null);
        add("inventory_transactions", "transaction_date", null);
        add("purchase_orders", "po_date", null);
        add("sales_orders", "order_date", null);
        add("sales_order_items", null, null);
        add("dispatches", "dispatch_date", null);
        add("production_orders", "start_date", null);
        add("ar_invoices", "invoice_date", FINANCE_EXTRA);
        add("ap_invoices", "invoice_date", FINANCE_EXTRA);
        add("journal_lines", "posting_date", FINANCE_EXTRA);
    }

    private void add(String entity, String dateColumn, String extra) {
        specs.put(entity, new ExportSpec(entity, "reporting." + entity, dateColumn, extra));
    }

    /** The entities the caller may export. */
    public List<String> entities() {
        List<String> out = new ArrayList<>();
        specs.values().forEach(s -> {
            if (s.extraAuthority() == null || has(s.extraAuthority())) {
                out.add(s.entity());
            }
        });
        return out;
    }

    /** Checks access, validates the format and records the export before any data is written. */
    public ExportSpec begin(String entity, String format, LocalDate from, LocalDate to) {
        ExportSpec spec = entity == null ? null : specs.get(entity.toLowerCase(Locale.ROOT));
        if (spec == null) {
            throw new NotFoundException("Export entity", entity);
        }
        if (spec.extraAuthority() != null && !has(spec.extraAuthority())) {
            throw new AccessDeniedException("Not permitted to export " + spec.entity());
        }
        if (!"csv".equals(format) && !"json".equals(format)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "format must be csv or json");
        }
        audit.record("DATA_EXPORT", "EXPORT_" + spec.entity().toUpperCase(Locale.ROOT), null, null,
                "{\"format\":\"" + format + "\",\"from\":" + quote(from) + ",\"to\":" + quote(to) + "}", null);
        return spec;
    }

    public void stream(ExportSpec spec, String format, LocalDate from, LocalDate to, OutputStream out)
            throws IOException {
        long maxRows = Math.max(1, settings.getInt("integration.export.max_rows", 1_000_000));
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        if ("csv".equals(format)) {
            writeCsv(spec, from, to, maxRows, writer);
        } else {
            writeJson(spec, from, to, maxRows, out);
        }
    }

    private void writeCsv(ExportSpec spec, LocalDate from, LocalDate to, long maxRows, Writer writer) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            boolean[] headerWritten = {false};
            page(spec, from, to, maxRows, rows -> {
                if (!headerWritten[0] && !rows.isEmpty()) {
                    printer.printRecord(rows.get(0).keySet());
                    headerWritten[0] = true;
                }
                for (Map<String, Object> row : rows) {
                    List<String> cells = new ArrayList<>();
                    row.values().forEach(v -> cells.add(csvCell(v)));
                    printer.printRecord(cells);
                }
            });
        }
    }

    private void writeJson(ExportSpec spec, LocalDate from, LocalDate to, long maxRows, OutputStream out)
            throws IOException {
        try (JsonGenerator gen = mapper.getFactory().createGenerator(out, JsonEncoding.UTF8)) {
            gen.writeStartArray();
            page(spec, from, to, maxRows, rows -> {
                for (Map<String, Object> row : rows) {
                    mapper.writeValue(gen, row);
                }
            });
            gen.writeEndArray();
        }
    }

    private interface BatchSink {
        void accept(List<Map<String, Object>> rows) throws IOException;
    }

    private void page(ExportSpec spec, LocalDate from, LocalDate to, long maxRows, BatchSink sink) throws IOException {
        String dateFilter = spec.dateColumn() == null ? ""
                : " AND " + spec.dateColumn() + " BETWEEN CAST(:from AS date) AND CAST(:to AS date)";
        String sql = "SELECT * FROM " + spec.view() + " WHERE id > :after" + dateFilter + " ORDER BY id LIMIT :batch";
        long after = 0;
        long written = 0;
        while (written < maxRows) {
            int batch = (int) Math.min(BATCH, maxRows - written);
            List<Map<String, Object>> rows = jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("after", after)
                    .addValue("from", Date.valueOf(QueryBounds.from(from)))
                    .addValue("to", Date.valueOf(QueryBounds.to(to)))
                    .addValue("batch", batch));
            if (rows.isEmpty()) {
                return;
            }
            rows = rows.stream().map(ExportService::normalise).toList();
            sink.accept(rows);
            after = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
            written += rows.size();
            if (rows.size() < batch) {
                return;
            }
        }
    }

    /** Dates and timestamps as ISO text, so CSV and JSON agree and carry no driver-specific format. */
    static Map<String, Object> normalise(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (v instanceof Date d) {
                out.put(k, d.toLocalDate().toString());
            } else if (v instanceof java.sql.Timestamp t) {
                out.put(k, t.toInstant().toString());
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !isNumber(value)) {
            return "'" + s;
        }
        return s;
    }

    private static boolean isNumber(Object value) {
        return value instanceof Number;
    }

    private static String quote(LocalDate d) {
        return d == null ? "null" : "\"" + d + "\"";
    }

    private static boolean has(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}

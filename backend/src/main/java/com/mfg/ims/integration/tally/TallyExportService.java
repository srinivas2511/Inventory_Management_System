package com.mfg.ims.integration.tally;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;

/**
 * Exports the chart of accounts and posted journals as Tally import XML (INT-007, R19-6.4): ledgers first, then one
 * Journal voucher per posted journal. Debit lines carry {@code ISDEEMEDPOSITIVE=Yes} and a negative amount, credit
 * lines {@code No} and a positive one, as Tally expects. A ledger is named "code name" so vouchers and masters always
 * agree. The format follows Tally's published import schema but has not been verified against a live Tally instance;
 * try a small period in a test company first.
 */
@Service
public class TallyExportService {

    static final int MAX_VOUCHERS = 20_000;
    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public TallyExportService(NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public String ledgers() {
        List<String> ledgers = new ArrayList<>();
        jdbc.query("SELECT account_code, name, account_type FROM ims.gl_accounts WHERE active = TRUE ORDER BY account_code",
                rs -> {
                    ledgers.add("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\"><LEDGER NAME=\"" + escape(ledgerName(
                            rs.getString("account_code"), rs.getString("name"))) + "\" ACTION=\"Create\">"
                            + "<NAME>" + escape(ledgerName(rs.getString("account_code"), rs.getString("name"))) + "</NAME>"
                            + "<PARENT>" + escape(tallyGroup(rs.getString("account_type"))) + "</PARENT>"
                            + "</LEDGER></TALLYMESSAGE>");
                });
        audit.record("TALLY_EXPORT", "TALLY_LEDGERS", null, null, "{\"ledgers\":" + ledgers.size() + "}", null);
        return envelope("All Masters", String.join("\n", ledgers));
    }

    public String vouchers(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "from must not be after to");
        }
        Map<Long, Voucher> byId = new LinkedHashMap<>();
        jdbc.query("""
                SELECT jh.id, jh.journal_number, jh.posting_date, jh.description,
                       a.account_code, a.name, jl.debit_amount, jl.credit_amount
                  FROM ims.journal_headers jh
                  JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                  JOIN ims.gl_accounts a    ON a.id = jl.gl_account_id
                 WHERE jh.status IN ('POSTED','REVERSED')
                   AND jh.posting_date BETWEEN CAST(:from AS date) AND CAST(:to AS date)
                 ORDER BY jh.posting_date, jh.id, jl.line_number
                """, new MapSqlParameterSource("from", Date.valueOf(start)).addValue("to", Date.valueOf(end)), rs -> {
            long id = rs.getLong("id");
            Voucher v = byId.get(id);
            if (v == null) {
                if (byId.size() >= MAX_VOUCHERS) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "More than " + MAX_VOUCHERS + " vouchers in the period; choose a shorter period");
                }
                v = new Voucher(rs.getString("journal_number"), rs.getDate("posting_date").toLocalDate(),
                        rs.getString("description"), new ArrayList<>());
                byId.put(id, v);
            }
            v.lines().add(new Line(ledgerName(rs.getString("account_code"), rs.getString("name")),
                    rs.getBigDecimal("debit_amount"), rs.getBigDecimal("credit_amount")));
        });
        StringBuilder body = new StringBuilder();
        for (Voucher v : byId.values()) {
            body.append("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\"><VOUCHER VCHTYPE=\"Journal\" ACTION=\"Create\">")
                    .append("<DATE>").append(v.date().format(TALLY_DATE)).append("</DATE>")
                    .append("<VOUCHERTYPENAME>Journal</VOUCHERTYPENAME>")
                    .append("<VOUCHERNUMBER>").append(escape(v.number())).append("</VOUCHERNUMBER>")
                    .append("<NARRATION>").append(escape(v.narration())).append("</NARRATION>");
            for (Line l : v.lines()) {
                boolean debit = l.debit().signum() > 0;
                BigDecimal amount = (debit ? l.debit().negate() : l.credit()).setScale(2, RoundingMode.HALF_UP);
                body.append("<ALLLEDGERENTRIES.LIST><LEDGERNAME>").append(escape(l.ledger())).append("</LEDGERNAME>")
                        .append("<ISDEEMEDPOSITIVE>").append(debit ? "Yes" : "No").append("</ISDEEMEDPOSITIVE>")
                        .append("<AMOUNT>").append(amount.toPlainString()).append("</AMOUNT></ALLLEDGERENTRIES.LIST>");
            }
            body.append("</VOUCHER></TALLYMESSAGE>\n");
        }
        audit.record("TALLY_EXPORT", "TALLY_VOUCHERS", null, null,
                "{\"from\":\"" + start + "\",\"to\":\"" + end + "\",\"vouchers\":" + byId.size() + "}", null);
        return envelope("Vouchers", body.toString());
    }

    private record Line(String ledger, BigDecimal debit, BigDecimal credit) {
    }

    private record Voucher(String number, LocalDate date, String narration, List<Line> lines) {
    }

    private String envelope(String report, String messages) {
        String company = companyName();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ENVELOPE><HEADER><TALLYREQUEST>Import Data</TALLYREQUEST></HEADER>"
                + "<BODY><IMPORTDATA><REQUESTDESC><REPORTNAME>" + report + "</REPORTNAME><STATICVARIABLES>"
                + "<SVCURRENTCOMPANY>" + escape(company) + "</SVCURRENTCOMPANY></STATICVARIABLES></REQUESTDESC>"
                + "<REQUESTDATA>\n" + messages + "\n</REQUESTDATA></IMPORTDATA></BODY></ENVELOPE>\n";
    }

    private String companyName() {
        List<String> names = jdbc.getJdbcOperations().queryForList("SELECT name FROM ims.companies ORDER BY id LIMIT 1",
                String.class);
        return names.isEmpty() ? "" : names.get(0);
    }

    static String ledgerName(String code, String name) {
        return code + " " + name;
    }

    /** Default Tally groups; adjust in Tally if the company's ledgers are grouped differently. */
    static String tallyGroup(String accountType) {
        return switch (accountType) {
            case "ASSET" -> "Current Assets";
            case "LIABILITY" -> "Current Liabilities";
            case "EQUITY" -> "Capital Account";
            case "REVENUE" -> "Sales Accounts";
            case "COGS" -> "Direct Expenses";
            default -> "Indirect Expenses";
        };
    }

    /** Escapes the five XML characters and drops characters XML 1.0 cannot carry. */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        text.codePoints().forEach(cp -> {
            boolean valid = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!valid) {
                return;
            }
            switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}

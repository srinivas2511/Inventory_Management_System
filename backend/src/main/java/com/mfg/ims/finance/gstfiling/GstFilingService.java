package com.mfg.ims.finance.gstfiling;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.gstfiling.GstFilingDtos.*;

@Service
public class GstFilingService {

    private final NamedParameterJdbcTemplate jdbc;

    public GstFilingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Gstr1Dto gstr1(LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource("from", from).addValue("to", to);

        String b2bSql = """
            SELECT COALESCE(c.gst_number,'') AS customer_gstin, si.doc_no, si.doc_date,
                   si.taxable_amount, si.grand_total, si.tax_amount
            FROM ims.sales_invoices si
            JOIN ims.customers c ON c.id = si.customer_id
            WHERE si.status = 'POSTED' AND si.supply_type = 'B2B'
              AND si.doc_date >= :from AND si.doc_date <= :to
            ORDER BY si.doc_date, si.doc_no
            """;
        List<B2bInvoice> b2b;
        try {
            b2b = jdbc.query(b2bSql, params, (rs, rn) -> new B2bInvoice(
                rs.getString("customer_gstin"),
                rs.getString("doc_no"),
                rs.getObject("doc_date", LocalDate.class),
                safeBd(rs.getBigDecimal("taxable_amount")),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                safeBd(rs.getBigDecimal("grand_total"))
            ));
        } catch (Exception e) {
            b2b = new ArrayList<>();
        }

        String b2cSql = """
            SELECT COALESCE(si.place_of_supply, '00') AS state_code,
                   COALESCE(SUM(si.taxable_amount),0) AS taxable_value,
                   COALESCE(SUM(si.tax_amount),0) AS tax_amount
            FROM ims.sales_invoices si
            WHERE si.status = 'POSTED' AND si.supply_type = 'B2C'
              AND si.doc_date >= :from AND si.doc_date <= :to
            GROUP BY si.place_of_supply
            ORDER BY state_code
            """;
        List<B2cAggregate> b2c;
        try {
            b2c = jdbc.query(b2cSql, params, (rs, rn) -> new B2cAggregate(
                rs.getString("state_code"),
                BigDecimal.ZERO,
                safeBd(rs.getBigDecimal("taxable_value")),
                safeBd(rs.getBigDecimal("tax_amount"))
            ));
        } catch (Exception e) {
            b2c = new ArrayList<>();
        }

        String hsnSql = """
            SELECT COALESCE(sii.hsn_sac, '9999') AS hsn_sac,
                   SUM(sii.qty) AS qty,
                   COALESCE(SUM(sii.taxable_value),0) AS taxable_value,
                   COALESCE(SUM(sii.tax_amount),0) AS tax_amount
            FROM ims.sales_invoice_items sii
            JOIN ims.sales_invoices si ON si.id = sii.invoice_id
            WHERE si.status = 'POSTED'
              AND si.doc_date >= :from AND si.doc_date <= :to
            GROUP BY sii.hsn_sac
            ORDER BY hsn_sac
            """;
        List<HsnSummary> hsnSummary;
        try {
            hsnSummary = jdbc.query(hsnSql, params, (rs, rn) -> new HsnSummary(
                rs.getString("hsn_sac"),
                safeBd(rs.getBigDecimal("qty")),
                safeBd(rs.getBigDecimal("taxable_value")),
                safeBd(rs.getBigDecimal("tax_amount"))
            ));
        } catch (Exception e) {
            hsnSummary = new ArrayList<>();
        }

        long invoiceCount = countInvoices(from, to);
        DocSummary docSummary = new DocSummary(invoiceCount, 0);

        return new Gstr1Dto(from, to, b2b, b2c, hsnSummary, docSummary);
    }

    public Gstr3bDto gstr3b(LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource("from", from).addValue("to", to);

        String outwardSql = """
            SELECT COALESCE(SUM(si.taxable_amount), 0) AS turnover
            FROM ims.sales_invoices si
            WHERE si.status = 'POSTED'
              AND si.doc_date >= :from AND si.doc_date <= :to
            """;
        BigDecimal turnover = jdbc.queryForObject(outwardSql, params, BigDecimal.class);

        // sum tax by type from invoice_tax_lines for SALES direction
        String outLiabSql = """
            SELECT tl.tax_type,
                   COALESCE(SUM(tl.tax_amount), 0) AS total
            FROM ims.invoice_tax_lines tl
            JOIN ims.sales_invoices si ON si.id = tl.document_id
            WHERE tl.document_type = 'SALES_INVOICE' AND si.status = 'POSTED'
              AND si.doc_date >= :from AND si.doc_date <= :to
            GROUP BY tl.tax_type
            """;
        TaxLiability outwardLiab = buildLiabilityFromRows(jdbc.queryForList(outLiabSql, params));

        String itcSql = """
            SELECT tl.tax_type,
                   COALESCE(SUM(tl.tax_amount), 0) AS total
            FROM ims.invoice_tax_lines tl
            JOIN ims.ap_invoices ai ON ai.id = tl.document_id
            WHERE tl.document_type = 'AP_INVOICE'
            GROUP BY tl.tax_type
            """;
        TaxLiability itc;
        try {
            itc = buildLiabilityFromRows(jdbc.queryForList(itcSql, Map.of()));
        } catch (Exception e) {
            itc = new TaxLiability(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal netPayable = outwardLiab.igst().add(outwardLiab.cgst()).add(outwardLiab.sgst())
            .subtract(itc.igst()).subtract(itc.cgst()).subtract(itc.sgst());

        return new Gstr3bDto(from, to, safeBd(turnover), outwardLiab, itc, netPayable);
    }

    public Map<String, Object> ewayBillJson(Long salesInvoiceId) {
        if (salesInvoiceId == null) throw new NotFoundException("SalesInvoice", "null");
        String sql = """
            SELECT si.doc_no, si.doc_date, si.taxable_amount, si.tax_amount, si.grand_total,
                   c.name AS customer_name, COALESCE(c.gst_number,'') AS customer_gstin,
                   COALESCE(c.address,'') AS customer_address,
                   co.name AS company_name, co.gstin AS company_gstin
            FROM ims.sales_invoices si
            JOIN ims.customers c ON c.id = si.customer_id
            JOIN ims.companies co ON co.id = 1
            WHERE si.id = :id
            """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, new MapSqlParameterSource("id", salesInvoiceId));
        } catch (Exception e) {
            throw new NotFoundException("SalesInvoice", salesInvoiceId.toString());
        }
        if (rows.isEmpty()) throw new NotFoundException("SalesInvoice", salesInvoiceId.toString());
        Map<String, Object> inv = rows.get(0);

        String itemSql = """
            SELECT sii.description, sii.qty, sii.rate,
                   COALESCE(sii.taxable_value, sii.qty * COALESCE(sii.rate, 0)) AS taxable_value,
                   COALESCE(p.product_code, '9999') AS hsn_code
            FROM ims.sales_invoice_items sii
            LEFT JOIN ims.products p ON p.id = sii.product_id
            WHERE sii.invoice_id = :id
            """;
        List<Map<String, Object>> items = jdbc.queryForList(itemSql, new MapSqlParameterSource("id", salesInvoiceId));

        String dispatchDate = inv.get("doc_date") != null ? inv.get("doc_date").toString().replace("-", "/") : "";

        var ewbItems = items.stream().map(it -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", strOf(it, "description"));
            row.put("hsnCode", strOf(it, "hsn_code"));
            row.put("quantity", safeDouble(it.get("qty")));
            row.put("taxableAmount", safeDouble(it.get("taxable_value")));
            return row;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplyType", "O");
        result.put("subSupplyType", "1");
        result.put("docType", "INV");
        result.put("docNo", strOf(inv, "doc_no"));
        result.put("docDate", dispatchDate);
        result.put("fromGstin", strOf(inv, "company_gstin"));
        result.put("fromTrdName", strOf(inv, "company_name"));
        result.put("toGstin", strOf(inv, "customer_gstin"));
        result.put("toTrdName", strOf(inv, "customer_name"));
        result.put("toAddr1", strOf(inv, "customer_address"));
        result.put("totalValue", safeDouble(inv.get("grand_total")));
        result.put("cgstValue", 0.0);
        result.put("sgstValue", 0.0);
        result.put("igstValue", safeDouble(inv.get("tax_amount")));
        result.put("transMode", "1");
        result.put("itemList", ewbItems);
        return result;
    }

    @Transactional
    public FilingRunDto markFiled(String returnType, LocalDate from, LocalDate to) {
        Long userId = currentUserId();
        String sql = """
            INSERT INTO ims.gst_filing_runs (return_type, period_from, period_to, status, filed_at, filed_by, row_count, created_by)
            VALUES (:rt, :from, :to, 'FILED', now(), :uid, :rc, :uid)
            RETURNING id, return_type, period_from, period_to, status, filed_at
            """;
        long rowCount = countInvoices(from, to);
        var params = new MapSqlParameterSource("rt", returnType).addValue("from", from).addValue("to", to)
            .addValue("uid", userId).addValue("rc", rowCount);
        return jdbc.queryForObject(sql, params, (rs, rn) -> new FilingRunDto(
            rs.getLong("id"), rs.getString("return_type"),
            rs.getObject("period_from", LocalDate.class),
            rs.getObject("period_to", LocalDate.class),
            rs.getString("status"),
            rs.getTimestamp("filed_at") != null ? rs.getTimestamp("filed_at").toInstant() : null
        ));
    }

    public List<FilingRunDto> filingHistory() {
        String sql = """
            SELECT id, return_type, period_from, period_to, status, filed_at
            FROM ims.gst_filing_runs ORDER BY created_at DESC LIMIT 100
            """;
        return jdbc.query(sql, Map.of(), (rs, rn) -> new FilingRunDto(
            rs.getLong("id"), rs.getString("return_type"),
            rs.getObject("period_from", LocalDate.class),
            rs.getObject("period_to", LocalDate.class),
            rs.getString("status"),
            rs.getTimestamp("filed_at") != null ? rs.getTimestamp("filed_at").toInstant() : null
        ));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TaxLiability buildLiabilityFromRows(List<Map<String, Object>> rows) {
        BigDecimal igst = BigDecimal.ZERO, cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            String taxType = r.getOrDefault("tax_type", "").toString().toUpperCase();
            BigDecimal total = safeBd((BigDecimal) r.get("total"));
            switch (taxType) {
                case "IGST" -> igst = igst.add(total);
                case "CGST" -> cgst = cgst.add(total);
                case "SGST", "UTGST" -> sgst = sgst.add(total);
            }
        }
        return new TaxLiability(igst, cgst, sgst);
    }

    private long countInvoices(LocalDate from, LocalDate to) {
        try {
            String sql = "SELECT COUNT(*) FROM ims.sales_invoices WHERE status = 'POSTED' AND doc_date >= :from AND doc_date <= :to";
            Long c = jdbc.queryForObject(sql, new MapSqlParameterSource("from", from).addValue("to", to), Long.class);
            return c != null ? c : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CachedUser u) return u.id();
        return 1L;
    }

    private static BigDecimal safeBd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static String strOf(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? v.toString() : ""; }
    private static double safeDouble(Object v) { if (v == null) return 0.0; return ((Number) v).doubleValue(); }
}

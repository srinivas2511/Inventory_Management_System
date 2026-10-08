package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

/**
 * R9-7.11 / FIN-007: the register of outward supplies for GST returns. One row per tax component of each customer
 * invoice, and a negative row for each posted credit note, in INR. The e-filing integration is R19-6.5.
 */
@Component
public class GstOutwardSuppliesReport implements ReportDefinition {

    private final EntityManager em;

    public GstOutwardSuppliesReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "GST_OUTWARD"; }
    @Override public String displayName() { return "GST outward supplies register"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Document", "Number", "Date", "Customer", "GSTIN", "Supply type", "Tax code", "Tax type",
                "Rate %", "Taxable value (INR)", "Tax (INR)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT x.doc, x.num, x.d, x.party, x.gstin, x.supply, x.tax_code, x.tax_type, x.rate_pct,
                       x.taxable, x.tax
                  FROM (
                        SELECT 'Invoice' AS doc, ai.invoice_number AS num, ai.invoice_date AS d, c.name AS party,
                               c.gst_number AS gstin, ai.supply_type AS supply, t.tax_code, t.tax_type, t.rate_pct,
                               ROUND(t.taxable_amount * ai.exchange_rate, 4) AS taxable,
                               ROUND(t.tax_amount * ai.exchange_rate, 4) AS tax
                          FROM ims.invoice_tax_lines t
                          JOIN ims.ar_invoices ai ON ai.id = t.document_id
                          JOIN ims.customers c ON c.id = ai.customer_id
                         WHERE t.document_type = 'AR_INVOICE' AND t.direction = 'SALES' AND ai.status <> 'CANCELLED'
                        UNION ALL
                        SELECT 'Credit note', cn.credit_note_number, cn.credit_date, c.name, c.gst_number,
                               ai.supply_type, t.tax_code, t.tax_type, t.rate_pct,
                               -ROUND(t.taxable_amount * ai.exchange_rate, 4),
                               -ROUND(t.tax_amount * ai.exchange_rate, 4)
                          FROM ims.invoice_tax_lines t
                          JOIN ims.credit_notes cn ON cn.id = t.document_id
                          JOIN ims.ar_invoices ai ON ai.id = cn.ar_invoice_id
                          JOIN ims.customers c ON c.id = ai.customer_id
                         WHERE t.document_type = 'CREDIT_NOTE' AND t.direction = 'SALES' AND cn.status = 'POSTED'
                       ) x
                 WHERE (CAST(:from AS date) IS NULL OR x.d >= CAST(:from AS date))
                   AND (CAST(:to AS date) IS NULL OR x.d <= CAST(:to AS date))
                 ORDER BY x.d, x.num, x.tax_type
                 LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("from", req.from() != null ? req.from().toString() : null);
        query.setParameter("to", req.to() != null ? req.to().toString() : null);
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

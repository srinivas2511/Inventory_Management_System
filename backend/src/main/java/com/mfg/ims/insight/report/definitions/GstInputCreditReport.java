package com.mfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

/**
 * R9-7.11 / FIN-007: the input tax credit register. One row per tax component of each supplier invoice that has been
 * opened (draft and cancelled invoices claim no credit), in INR.
 */
@Component
public class GstInputCreditReport implements ReportDefinition {

    private final EntityManager em;

    public GstInputCreditReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "GST_ITC"; }
    @Override public String displayName() { return "GST input tax credit register"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Invoice", "Supplier invoice", "Date", "Supplier", "GSTIN", "Supply type", "Tax code",
                "Tax type", "Rate %", "Taxable value (INR)", "Input tax (INR)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT ap.invoice_number, ap.supplier_invoice_no, ap.invoice_date, s.name, s.gst_number,
                       ap.supply_type, t.tax_code, t.tax_type, t.rate_pct,
                       ROUND(t.taxable_amount * ap.exchange_rate, 4) AS taxable,
                       ROUND(t.tax_amount * ap.exchange_rate, 4) AS tax
                  FROM ims.invoice_tax_lines t
                  JOIN ims.ap_invoices ap ON ap.id = t.document_id
                  JOIN ims.suppliers s ON s.id = ap.supplier_id
                 WHERE t.document_type = 'AP_INVOICE' AND t.direction = 'PURCHASE'
                   AND ap.status NOT IN ('DRAFT', 'CANCELLED')
                   AND (CAST(:from AS date) IS NULL OR ap.invoice_date >= CAST(:from AS date))
                   AND (CAST(:to AS date) IS NULL OR ap.invoice_date <= CAST(:to AS date))
                 ORDER BY ap.invoice_date, ap.invoice_number, t.tax_type
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

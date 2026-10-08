package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

/**
 * R9-7.8 / PUR-005: purchase price variance by supplier and month, captured when each supplier invoice is entered:
 * what was invoiced (INR, before tax) against the value of the goods receipt it bills. Cancelled invoices are left out.
 */
@Component
public class ApPriceVarianceReport implements ReportDefinition {

    private final EntityManager em;

    public ApPriceVarianceReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "AP_PRICE_VARIANCE"; }
    @Override public String displayName() { return "Supplier price variance"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Supplier", "Month", "Invoices", "Invoiced (INR)", "Price variance (INR)", "Variance %");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT s.name, to_char(date_trunc('month', ap.invoice_date), 'YYYY-MM') AS month,
                       COUNT(*) AS invoices,
                       SUM(ROUND(ap.subtotal * ap.exchange_rate, 4)) AS invoiced,
                       SUM(ap.price_variance_inr) AS variance,
                       ROUND(SUM(ap.price_variance_inr) * 100.0
                             / NULLIF(SUM(ROUND(ap.subtotal * ap.exchange_rate, 4)), 0), 2) AS variance_pct
                  FROM ims.ap_invoices ap
                  JOIN ims.suppliers s ON s.id = ap.supplier_id
                 WHERE ap.status <> 'CANCELLED'
                   AND (CAST(:from AS date) IS NULL OR ap.invoice_date >= CAST(:from AS date))
                   AND (CAST(:to AS date) IS NULL OR ap.invoice_date <= CAST(:to AS date))
                 GROUP BY s.name, date_trunc('month', ap.invoice_date)
                 ORDER BY date_trunc('month', ap.invoice_date) DESC, s.name
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

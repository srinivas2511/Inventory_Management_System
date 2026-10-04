package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

import jakarta.persistence.EntityManager;

/** Customer complaint report (RPT-004, QLT-COMPLAINT): complaints by customer, product, batch and category. */
@Component
public class QualityComplaintsReport implements ReportDefinition {

    private final EntityManager em;

    public QualityComplaintsReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "QUAL_COMPLAINTS"; }
    @Override public String displayName() { return "Customer Complaints Report"; }
    @Override public List<String> requiredAuthority() { return List.of("QUALITY_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Complaint Number", "Complaint Date", "Customer", "Product", "FG Batch", "Category",
                "Status", "Disposition", "Qty Complained", "Qty Returned", "CAPA");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT cc.complaint_number, cc.complaint_date, cu.name, p.product_code, fb.batch_number,
                       cc.category, cc.status, COALESCE(cc.disposition, ''), cc.quantity_complained,
                       cc.quantity_returned, COALESCE(ca.capa_number, '')
                FROM ims.customer_complaints cc
                JOIN ims.customers cu ON cu.id = cc.customer_id
                LEFT JOIN ims.products p ON p.id = cc.product_id
                LEFT JOIN ims.fg_batches fb ON fb.id = cc.fg_batch_id
                LEFT JOIN ims.capa_records ca ON ca.id = cc.capa_id
                WHERE cc.complaint_date >= CAST(:from AS date)
                  AND cc.complaint_date <= CAST(:to AS date)
                  AND (:status = '' OR cc.status = :status)
                ORDER BY cc.complaint_date DESC, cc.id DESC
                LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("from", req.from() != null ? req.from().toString() : "0001-01-01");
        query.setParameter("to", req.to() != null ? req.to().toString() : "9999-12-31");
        query.setParameter("status", SearchText.normalise(req.status()));
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

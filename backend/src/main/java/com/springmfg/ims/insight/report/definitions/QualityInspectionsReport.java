package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

@Component
public class QualityInspectionsReport implements ReportDefinition {

    private final EntityManager em;

    public QualityInspectionsReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "QUAL_INSPECTIONS"; }
    @Override public String displayName() { return "Quality Inspections Report"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Inspection Number", "Type", "Status", "Result",
                "Inspected Qty", "Rejected Qty", "Inspection Date");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT qi.inspection_number, qi.inspection_type, qi.status, qi.result,
                       qi.inspected_qty, qi.rejected_qty, qi.created_at::date AS inspection_date
                FROM ims.quality_inspections qi
                WHERE (:from IS NULL OR qi.created_at::date >= :from::date)
                  AND (:to   IS NULL OR qi.created_at::date <= :to::date)
                  AND (:status IS NULL OR qi.status = :status)
                ORDER BY qi.created_at DESC
                LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("from", req.from() != null ? req.from().toString() : null);
        query.setParameter("to", req.to() != null ? req.to().toString() : null);
        query.setParameter("status", req.status());
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

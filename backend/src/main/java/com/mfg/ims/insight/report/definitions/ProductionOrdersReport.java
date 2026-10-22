package com.mfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

@Component
public class ProductionOrdersReport implements ReportDefinition {

    private final EntityManager em;

    public ProductionOrdersReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "PROD_ORDERS"; }
    @Override public String displayName() { return "Production Orders Report"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Order Number", "Product", "Status", "Planned Qty",
                "Good Qty", "Rejected Qty", "Expected Completion");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT po.order_number, p.name AS product_name, po.status,
                       po.planned_qty, po.good_qty, po.rejected_qty, po.expected_completion_date
                FROM ims.production_orders po
                JOIN ims.products p ON p.id = po.product_id
                WHERE (CAST(:from AS date) IS NULL OR po.created_at::date >= CAST(:from AS date))
                  AND (CAST(:to AS date) IS NULL OR po.created_at::date <= CAST(:to AS date))
                  AND (CAST(:status AS varchar) IS NULL OR po.status = CAST(:status AS varchar))
                ORDER BY po.created_at DESC
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

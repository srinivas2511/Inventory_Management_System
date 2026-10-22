package com.mfg.ims.controlling.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

/** Actual production cost by element, product and month, with the variance against standard cost. */
@Component
public class CostByProductReport implements ReportDefinition {

    private final EntityManager em;

    public CostByProductReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "CTR_COST_BY_PRODUCT"; }
    @Override public String displayName() { return "Production Cost by Product and Period"; }
    @Override public List<String> requiredAuthority() { return List.of("CONTROLLING_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Product", "Period", "Orders", "Good Qty", "Material", "Labour", "Machine",
                "Overhead", "Actual Cost", "Standard Cost", "Variance");
    }

    @Override
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT p.product_code || ' - ' || p.name AS product,
                       to_char(v.calculated_at, 'YYYY-MM') AS period,
                       COUNT(*), SUM(v.good_qty),
                       SUM(v.actual_material_cost), SUM(v.actual_labour_cost), SUM(v.actual_machine_cost),
                       SUM(v.actual_overhead_cost), SUM(v.actual_cost), SUM(v.standard_cost), SUM(v.total_variance)
                FROM ims.production_order_variances v
                JOIN ims.products p ON p.id = v.product_id
                WHERE CAST(v.calculated_at AS date) BETWEEN :from AND :to
                  AND (:entityId = 0 OR v.product_id = :entityId)
                GROUP BY p.id, p.product_code, p.name, to_char(v.calculated_at, 'YYYY-MM')
                ORDER BY p.product_code, period
                LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("entityId", req.entityId() != null ? req.entityId() : 0L);
        query.setParameter("from", req.from() != null ? req.from() : LocalDate.of(1900, 1, 1));
        query.setParameter("to", req.to() != null ? req.to() : LocalDate.of(9999, 12, 31));
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

package com.mfg.ims.crm.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

/**
 * Opportunities by stage with count, value, probability-weighted value and the expected close date range (CRM-005).
 * The date range filters on the expected close date; entityId optionally restricts to one owner; status to one stage.
 */
@Component
public class CrmPipelineReport implements ReportDefinition {

    private final EntityManager em;

    public CrmPipelineReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "CRM_PIPELINE"; }
    @Override public String displayName() { return "Sales Pipeline by Stage"; }
    @Override public List<String> requiredAuthority() { return List.of("CRM_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Stage", "Opportunities", "Total Value", "Weighted Value", "Earliest Close", "Latest Close");
    }

    @Override
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT o.stage,
                       COUNT(*)                                                          AS opportunities,
                       COALESCE(SUM(o.estimated_value), 0)                               AS total_value,
                       COALESCE(SUM(o.estimated_value * o.probability_pct / 100.0), 0)   AS weighted_value,
                       MIN(o.expected_close_date)                                        AS earliest_close,
                       MAX(o.expected_close_date)                                        AS latest_close
                FROM ims.crm_opportunities o
                WHERE (:filterDates = FALSE OR o.expected_close_date BETWEEN :from AND :to)
                  AND (:ownerId = 0 OR o.owner_id = :ownerId)
                  AND (:stage = '' OR o.stage = :stage)
                GROUP BY o.stage
                ORDER BY CASE o.stage WHEN 'PROSPECT' THEN 1 WHEN 'QUALIFIED' THEN 2 WHEN 'PROPOSAL' THEN 3
                                      WHEN 'NEGOTIATION' THEN 4 WHEN 'CLOSED_WON' THEN 5 ELSE 6 END
                LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("filterDates", req.from() != null || req.to() != null);
        query.setParameter("from", req.from() != null ? req.from() : LocalDate.of(1900, 1, 1));
        query.setParameter("to", req.to() != null ? req.to() : LocalDate.of(9999, 12, 31));
        query.setParameter("ownerId", req.entityId() != null ? req.entityId() : 0L);
        query.setParameter("stage", req.status() != null ? req.status() : "");
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

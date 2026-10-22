package com.mfg.ims.maintenance.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

import jakarta.persistence.EntityManager;

/** Maintenance cost per machine: labour at the cost centre rate plus the spare parts issued, for requests completed in the range. */
@Component
public class MaintenanceCostReport implements ReportDefinition {

    private final EntityManager em;

    public MaintenanceCostReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "MAINT_COST"; }
    @Override public String displayName() { return "Maintenance Cost per Machine"; }
    @Override public List<String> requiredAuthority() { return List.of("MAINTENANCE_COST_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Machine", "Requests", "Labour Hours", "Labour Cost", "Spare Parts Cost", "Total Cost");
    }

    @Override
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT m.machine_code || ' - ' || m.name AS machine,
                       COUNT(r.id) AS requests,
                       COALESCE(SUM(r.labour_hours), 0) AS labour_hours,
                       COALESCE(SUM(r.labour_cost), 0) AS labour_cost,
                       COALESCE(SUM(sp.cost), 0) AS spare_cost,
                       COALESCE(SUM(r.labour_cost), 0) + COALESCE(SUM(sp.cost), 0) AS total_cost
                FROM ims.maintenance_requests r
                JOIN ims.machines m ON m.id = r.machine_id
                LEFT JOIN (SELECT maintenance_request_id, SUM(issued_qty * COALESCE(unit_cost, 0)) AS cost
                             FROM ims.spare_part_issue_lines GROUP BY maintenance_request_id) sp
                       ON sp.maintenance_request_id = r.id
                WHERE r.status = 'COMPLETED'
                  AND (:entityId = 0 OR r.machine_id = :entityId)
                  AND (r.completed_at AT TIME ZONE ims.business_tz())::date BETWEEN :from AND :to
                GROUP BY m.id, m.machine_code, m.name
                ORDER BY total_cost DESC, m.machine_code
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

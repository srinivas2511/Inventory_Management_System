package com.springmfg.ims.controlling.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

/** Posted expense/COGS debits per cost centre and fiscal period against the cost centre budget. */
@Component
public class CostCentreActualsReport implements ReportDefinition {

    private final EntityManager em;

    public CostCentreActualsReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "CTR_COST_CENTRE_ACTUALS"; }
    @Override public String displayName() { return "Cost Centre Actual vs Budget"; }
    @Override public List<String> requiredAuthority() { return List.of("CONTROLLING_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Cost Centre", "Period", "Actual Debit", "Actual Credit", "Net Actual", "Budget", "Over/(Under) Budget");
    }

    @Override
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                WITH actual AS (
                    SELECT jl.cost_centre_id, jh.period_id,
                           SUM(jl.debit_amount) AS dr, SUM(jl.credit_amount) AS cr
                    FROM ims.journal_lines jl
                    JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                    JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
                    WHERE jh.status IN ('POSTED', 'REVERSED')
                      AND jl.cost_centre_id IS NOT NULL
                      AND ga.account_type IN ('EXPENSE', 'COGS')
                      AND (:entityId = 0 OR jl.cost_centre_id = :entityId)
                    GROUP BY jl.cost_centre_id, jh.period_id
                ), budget AS (
                    SELECT b.cost_centre_id, b.period_id, SUM(b.budgeted_amount) AS amt
                    FROM ims.cost_centre_budgets b
                    WHERE (:entityId = 0 OR b.cost_centre_id = :entityId)
                    GROUP BY b.cost_centre_id, b.period_id
                ), keys AS (
                    SELECT cost_centre_id, period_id FROM actual
                    UNION
                    SELECT cost_centre_id, period_id FROM budget
                )
                SELECT cc.cost_centre_code || ' - ' || cc.name AS cost_centre,
                       fp.period_name,
                       COALESCE(a.dr, 0), COALESCE(a.cr, 0),
                       COALESCE(a.dr, 0) - COALESCE(a.cr, 0),
                       COALESCE(b.amt, 0),
                       COALESCE(a.dr, 0) - COALESCE(a.cr, 0) - COALESCE(b.amt, 0)
                FROM keys k
                JOIN ims.cost_centres cc ON cc.id = k.cost_centre_id
                JOIN ims.fiscal_periods fp ON fp.id = k.period_id
                LEFT JOIN actual a ON a.cost_centre_id = k.cost_centre_id AND a.period_id = k.period_id
                LEFT JOIN budget b ON b.cost_centre_id = k.cost_centre_id AND b.period_id = k.period_id
                WHERE fp.end_date >= :from AND fp.start_date <= :to
                ORDER BY cc.cost_centre_code, fp.start_date
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

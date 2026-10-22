package com.mfg.ims.controlling.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.mfg.ims.insight.report.ReportDefinition;
import com.mfg.ims.insight.report.ReportQueryHelper;
import com.mfg.ims.insight.report.ReportRequest;

/**
 * Revenue, COGS and gross margin per profit centre from posted journal lines. Production variance accounts
 * (5204 expense, 4101 income) are reported separately so they do not distort revenue.
 */
@Component
public class ProfitCentreMarginReport implements ReportDefinition {

    private final EntityManager em;

    public ProfitCentreMarginReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "CTR_PROFIT_CENTRE_MARGIN"; }
    @Override public String displayName() { return "Profit Centre Margin"; }
    @Override public List<String> requiredAuthority() { return List.of("CONTROLLING_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Profit Centre", "Revenue", "COGS", "Gross Margin", "Margin %", "Net Production Variance");
    }

    @Override
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                WITH agg AS (
                    SELECT pc.id, pc.profit_centre_code, pc.name,
                           COALESCE(SUM(CASE WHEN ga.account_type = 'REVENUE' AND ga.account_code <> '4101'
                                             THEN jl.credit_amount - jl.debit_amount END), 0) AS revenue,
                           COALESCE(SUM(CASE WHEN ga.account_type = 'COGS'
                                             THEN jl.debit_amount - jl.credit_amount END), 0) AS cogs,
                           COALESCE(SUM(CASE WHEN ga.account_code IN ('5204', '4101')
                                             THEN jl.debit_amount - jl.credit_amount END), 0) AS variance
                    FROM ims.journal_lines jl
                    JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                    JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
                    JOIN ims.profit_centres pc ON pc.id = jl.profit_centre_id
                    WHERE jh.status IN ('POSTED', 'REVERSED')
                      AND jh.posting_date BETWEEN :from AND :to
                      AND (:entityId = 0 OR pc.id = :entityId)
                    GROUP BY pc.id, pc.profit_centre_code, pc.name
                )
                SELECT profit_centre_code || ' - ' || name AS profit_centre,
                       revenue, cogs, revenue - cogs AS gross_margin,
                       CASE WHEN revenue = 0 THEN NULL ELSE ROUND((revenue - cogs) * 100 / revenue, 2) END AS margin_pct,
                       variance
                FROM agg
                ORDER BY profit_centre_code
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

package com.springmfg.ims.insight.dashboard;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.insight.dashboard.DashboardTrendsDto.InventoryValueMonth;
import com.springmfg.ims.insight.dashboard.DashboardTrendsDto.ProductionMonth;

/**
 * Reads the dashboard's monthly series straight from the ledger and the production orders. The materialized views
 * behind the analytics module only refresh on a schedule, so a freshly loaded system would show an empty chart.
 */
@Service
public class DashboardTrendService {

    static final int MAX_MONTHS = 24;

    private final JdbcTemplate jdbc;

    public DashboardTrendService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardTrendsDto trends(int requestedMonths) {
        int months = Math.min(Math.max(requestedMonths, 1), MAX_MONTHS);
        List<ProductionMonth> production = jdbc.query("""
                SELECT month, good_qty, rejected_qty FROM (
                    SELECT date_trunc('month', po.created_at)::date AS month,
                           COALESCE(SUM(po.good_qty), 0) AS good_qty,
                           COALESCE(SUM(po.rejected_qty), 0) AS rejected_qty
                      FROM ims.production_orders po
                     WHERE po.status NOT IN ('DRAFT', 'CANCELLED')
                     GROUP BY 1
                     ORDER BY 1 DESC
                     LIMIT ?
                ) recent ORDER BY month
                """, (rs, i) -> new ProductionMonth(rs.getDate("month").toLocalDate(),
                scale(rs.getBigDecimal("good_qty")), scale(rs.getBigDecimal("rejected_qty"))), months);

        List<InventoryValueMonth> inventoryValue = jdbc.query("""
                SELECT month, closing_value FROM (
                    SELECT month, SUM(net) OVER (ORDER BY month) AS closing_value FROM (
                        SELECT date_trunc('month', transaction_date)::date AS month,
                               SUM(CASE WHEN quantity > 0 THEN total_cost ELSE -ABS(total_cost) END) AS net
                          FROM ims.inventory_transactions
                         GROUP BY 1
                    ) flows
                ) cumulative ORDER BY month DESC LIMIT ?
                """, (rs, i) -> new InventoryValueMonth(rs.getDate("month").toLocalDate(),
                scale(rs.getBigDecimal("closing_value"))), months);
        // the query returns the newest months first so the LIMIT keeps the recent ones; the chart wants oldest first
        List<InventoryValueMonth> ascending = inventoryValue.stream()
                .sorted((a, b) -> a.month().compareTo(b.month())).toList();
        return new DashboardTrendsDto(production, ascending);
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}

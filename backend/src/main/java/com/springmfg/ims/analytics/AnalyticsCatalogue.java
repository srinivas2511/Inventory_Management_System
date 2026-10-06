package com.springmfg.ims.analytics;

import static com.springmfg.ims.analytics.AnalyticsQuery.date;
import static com.springmfg.ims.analytics.AnalyticsQuery.money;
import static com.springmfg.ims.analytics.AnalyticsQuery.number;
import static com.springmfg.ims.analytics.AnalyticsQuery.percent;
import static com.springmfg.ims.analytics.AnalyticsQuery.text;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** The query catalogue behind {@code GET /api/analytics/query} (R19-2.2) and the analytical dashboards (R19-5.1). */
@Component
public class AnalyticsCatalogue {

    public static final String VIEW = "ANALYTICS_VIEW";
    public static final String FINANCE = "ANALYTICS_FINANCE";

    private static final String RANGE = "BETWEEN CAST(:from AS date) AND CAST(:to AS date)";

    private final List<AnalyticsQuery> queries = List.of(
            new AnalyticsQuery("FIN_SUMMARY_MONTHLY", "Financial summary by period",
                    "Revenue, cost of goods sold, expenses and profit per fiscal period, from posted journals.",
                    "Finance", FINANCE, true, "BAR", "period_name", List.of("revenue", "cogs", "net_profit"),
                    List.of(text("period_name", "Period"), date("month", "Month"), money("revenue", "Revenue"),
                            money("cogs", "COGS"), money("gross_profit", "Gross profit"),
                            money("expenses", "Expenses"), money("net_profit", "Net profit")),
                    """
                    SELECT period_name, month, revenue, cogs, (revenue - cogs) AS gross_profit, expenses,
                           (revenue - cogs - expenses) AS net_profit
                      FROM ims.mv_financial_summary_monthly
                     WHERE month %s
                     ORDER BY month LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("SUPPLIER_SCORECARD", "Supplier scorecard",
                    "On-time delivery, incoming quality pass rate and price variance per supplier and month.",
                    "Purchasing", VIEW, true, "TABLE", "supplier_name", List.of("on_time_pct", "quality_pass_pct"),
                    List.of(text("supplier_code", "Code"), text("supplier_name", "Supplier"), date("month", "Month"),
                            number("po_count", "POs"), money("ordered_value", "Ordered value"),
                            percent("on_time_pct", "On time %"), number("received_qty", "Received qty"),
                            number("accepted_qty", "Accepted qty"), number("rejected_qty", "Rejected qty"),
                            percent("quality_pass_pct", "Quality pass %"),
                            percent("price_variance_pct", "Price variance %")),
                    """
                    SELECT s.supplier_code, s.name AS supplier_name, p.month, p.po_count, p.ordered_value,
                           p.on_time_pct, p.received_qty, p.accepted_qty, p.rejected_qty, p.quality_pass_pct,
                           p.price_variance_pct
                      FROM ims.mv_supplier_performance p
                      JOIN ims.suppliers s ON s.id = p.supplier_id
                     WHERE p.month %s
                     ORDER BY p.month, s.supplier_code LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("CUSTOMER_SALES_SUMMARY", "Customer sales summary",
                    "Orders, dispatches, invoiced and received amounts per customer and month.",
                    "Sales", VIEW, true, "TABLE", "customer_name", List.of("dispatched_value", "invoiced_total"),
                    List.of(text("customer_code", "Code"), text("customer_name", "Customer"), date("month", "Month"),
                            number("orders", "Orders"), money("ordered_value", "Ordered value"),
                            number("dispatches", "Dispatches"), money("dispatched_value", "Dispatched value"),
                            money("invoiced_total", "Invoiced"), money("received_total", "Received")),
                    """
                    SELECT c.customer_code, c.name AS customer_name, m.month, m.orders, m.ordered_value,
                           m.dispatches, m.dispatched_value, m.invoiced_total, m.received_total
                      FROM ims.mv_customer_sales_summary m
                      JOIN ims.customers c ON c.id = m.customer_id
                     WHERE m.month %s
                     ORDER BY m.month, c.customer_code LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("CUSTOMER_PROFITABILITY", "Customer profitability",
                    "Dispatched value less the cost of the stock dispatched, per customer.",
                    "Sales", FINANCE, false, "BAR", "customer_name", List.of("revenue", "gross_margin"),
                    List.of(text("customer_code", "Code"), text("customer_name", "Customer"),
                            money("revenue", "Revenue"), money("cogs", "COGS"),
                            money("gross_margin", "Gross margin"), percent("margin_pct", "Margin %")),
                    """
                    SELECT c.customer_code, c.name AS customer_name,
                           rev.revenue, COALESCE(cg.cogs, 0) AS cogs,
                           rev.revenue - COALESCE(cg.cogs, 0) AS gross_margin,
                           CASE WHEN rev.revenue = 0 THEN NULL
                                ELSE ROUND((rev.revenue - COALESCE(cg.cogs, 0)) * 100.0 / rev.revenue, 2) END AS margin_pct
                      FROM ims.customers c
                      JOIN (SELECT d.customer_id, SUM(di.quantity * COALESCE(di.unit_price, 0)) AS revenue
                              FROM ims.dispatches d
                              JOIN ims.dispatch_items di ON di.dispatch_id = d.id
                             WHERE d.dispatch_date %1$s
                             GROUP BY d.customer_id) rev ON rev.customer_id = c.id
                      LEFT JOIN (SELECT d.customer_id, SUM(ABS(it.total_cost)) AS cogs
                                   FROM ims.dispatches d
                                   JOIN ims.inventory_transactions it
                                     ON it.reference_type = 'DISPATCH' AND it.reference_id = d.id
                                    AND it.transaction_type = 'SALES_DISPATCH'
                                  WHERE d.dispatch_date %1$s
                                  GROUP BY d.customer_id) cg ON cg.customer_id = c.id
                     ORDER BY gross_margin DESC LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("INVENTORY_TURNS", "Inventory turns",
                    "Cost of goods dispatched against the average inventory value, annualised, per month.",
                    "Inventory", VIEW, true, "LINE", "month", List.of("annualised_turns"),
                    List.of(date("month", "Month"), money("opening_value", "Opening value"),
                            money("closing_value", "Closing value"), money("cogs", "COGS"),
                            number("annualised_turns", "Turns (annualised)")),
                    """
                    WITH flows AS (
                        SELECT month, SUM(value_in - value_out) AS net FROM ims.mv_inventory_value_monthly GROUP BY month
                    ), cum AS (
                        SELECT month, SUM(net) OVER (ORDER BY month) AS closing_value FROM flows
                    ), cogs AS (
                        SELECT date_trunc('month', transaction_date)::date AS month, SUM(ABS(total_cost)) AS cogs
                          FROM ims.inventory_transactions
                         WHERE transaction_type = 'SALES_DISPATCH' AND quantity < 0
                         GROUP BY 1
                    ), x AS (
                        SELECT c.month, LAG(c.closing_value) OVER (ORDER BY c.month) AS opening_value,
                               c.closing_value, COALESCE(g.cogs, 0) AS cogs
                          FROM cum c LEFT JOIN cogs g ON g.month = c.month
                    )
                    SELECT month, opening_value, closing_value, cogs,
                           CASE WHEN (COALESCE(opening_value, 0) + closing_value) <= 0 THEN NULL
                                ELSE ROUND(cogs * 12 / ((COALESCE(opening_value, 0) + closing_value) / 2), 2) END
                                AS annualised_turns
                      FROM x
                     WHERE month %s
                     ORDER BY month LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("OEE_DAILY", "Machine output and downtime per day",
                    "Good and bad output, ideal minutes and downtime per machine per day (the OEE building blocks).",
                    "Production", VIEW, false, "TABLE", "day", List.of("quality_pct"),
                    List.of(text("machine_code", "Machine"), text("machine_name", "Name"), date("day", "Day"),
                            number("good_qty", "Good"), number("bad_qty", "Bad"),
                            number("ideal_minutes", "Ideal min"), number("bad_ideal_minutes", "Bad ideal min"),
                            number("planned_downtime_min", "Planned downtime min"),
                            number("unplanned_downtime_min", "Unplanned downtime min"),
                            percent("quality_pct", "Quality %")),
                    """
                    SELECT m.machine_code, m.name AS machine_name, o.day, o.good_qty, o.bad_qty, o.ideal_minutes,
                           o.bad_ideal_minutes, o.planned_downtime_min, o.unplanned_downtime_min,
                           CASE WHEN o.good_qty + o.bad_qty = 0 THEN NULL
                                ELSE ROUND(o.good_qty * 100.0 / (o.good_qty + o.bad_qty), 2) END AS quality_pct
                      FROM ims.mv_oee_daily o
                      JOIN ims.machines m ON m.id = o.machine_id
                     WHERE o.day %s
                     ORDER BY o.day, m.machine_code LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("PRODUCTION_MONTHLY", "Production by month",
                    "Production orders, planned and good quantity and yield per month.",
                    "Production", VIEW, true, "BAR", "month", List.of("planned_qty", "good_qty"),
                    List.of(date("month", "Month"), number("total_orders", "Orders"),
                            number("planned_qty", "Planned qty"), number("good_qty", "Good qty"),
                            number("rejected_qty", "Rejected qty"), percent("yield_pct", "Yield %")),
                    """
                    SELECT month, total_orders, planned_qty, good_qty, rejected_qty, yield_pct
                      FROM ims.mv_production_monthly
                     WHERE month %s
                     ORDER BY month LIMIT :limit
                    """.formatted(RANGE)),

            new AnalyticsQuery("COST_VARIANCE", "Production cost variance",
                    "Standard against actual cost per closed production order, split into price, quantity and efficiency variance.",
                    "Production", FINANCE, false, "BAR", "order_number", List.of("total_variance"),
                    List.of(text("order_number", "Order"), text("product_code", "Product"),
                            number("good_qty", "Good qty"), money("standard_cost", "Standard cost"),
                            money("actual_cost", "Actual cost"), money("price_variance", "Price variance"),
                            money("quantity_variance", "Quantity variance"),
                            money("efficiency_variance", "Efficiency variance"),
                            money("other_variance", "Other variance"), money("total_variance", "Total variance"),
                            date("calculated_on", "Calculated")),
                    """
                    SELECT po.order_number, p.product_code, v.good_qty, v.standard_cost, v.actual_cost,
                           v.price_variance, v.quantity_variance, v.efficiency_variance, v.other_variance,
                           v.total_variance, CAST(v.calculated_at AS date) AS calculated_on
                      FROM ims.production_order_variances v
                      JOIN ims.production_orders po ON po.id = v.production_order_id
                      JOIN ims.products p ON p.id = v.product_id
                     WHERE CAST(v.calculated_at AS date) %s
                     ORDER BY v.calculated_at DESC LIMIT :limit
                    """.formatted(RANGE)));

    private final Map<String, AnalyticsQuery> byKey =
            queries.stream().collect(Collectors.toMap(AnalyticsQuery::key, Function.identity()));

    public List<AnalyticsQuery> all() {
        return queries;
    }

    public AnalyticsQuery find(String key) {
        return key == null ? null : byKey.get(key.trim().toUpperCase());
    }
}

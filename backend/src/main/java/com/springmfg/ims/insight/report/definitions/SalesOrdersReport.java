package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

@Component
public class SalesOrdersReport implements ReportDefinition {

    private final EntityManager em;

    public SalesOrdersReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "SALES_ORDERS_RPT"; }
    @Override public String displayName() { return "Sales Orders Report"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Order Number", "Customer", "Status", "Order Date",
                "Required By", "Total Amount (INR)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT so.order_number, c.name AS customer_name, so.status,
                       so.order_date, so.required_date AS required_by_date,
                       COALESCE((SELECT SUM(i.unit_price * i.ordered_qty) FROM ims.sales_order_items i
                                 WHERE i.sales_order_id = so.id AND i.unit_price IS NOT NULL), 0) AS total_amount
                FROM ims.sales_orders so
                JOIN ims.customers c ON c.id = so.customer_id
                WHERE (CAST(:from AS date) IS NULL OR so.order_date >= CAST(:from AS date))
                  AND (CAST(:to AS date) IS NULL OR so.order_date <= CAST(:to AS date))
                  AND (CAST(:status AS varchar) IS NULL OR so.status = CAST(:status AS varchar))
                ORDER BY so.order_date DESC
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

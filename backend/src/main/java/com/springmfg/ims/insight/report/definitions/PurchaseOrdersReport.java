package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

@Component
public class PurchaseOrdersReport implements ReportDefinition {

    private final EntityManager em;

    public PurchaseOrdersReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "PURCH_ORDERS"; }
    @Override public String displayName() { return "Purchase Orders Report"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("PO Number", "Supplier", "Status", "Order Date",
                "Expected Delivery", "Total Amount (INR)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT po.po_number, s.name AS supplier_name, po.status,
                       po.order_date, po.expected_delivery_date,
                       po.total_amount
                FROM ims.purchase_orders po
                JOIN ims.suppliers s ON s.id = po.supplier_id
                WHERE (:from IS NULL OR po.order_date >= :from::date)
                  AND (:to   IS NULL OR po.order_date <= :to::date)
                  AND (:status IS NULL OR po.status = :status)
                ORDER BY po.order_date DESC
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

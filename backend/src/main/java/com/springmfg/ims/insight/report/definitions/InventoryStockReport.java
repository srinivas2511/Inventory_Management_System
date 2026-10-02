package com.springmfg.ims.insight.report.definitions;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.springmfg.ims.insight.report.ReportDefinition;
import com.springmfg.ims.insight.report.ReportQueryHelper;
import com.springmfg.ims.insight.report.ReportRequest;

@Component
public class InventoryStockReport implements ReportDefinition {

    private final EntityManager em;

    public InventoryStockReport(EntityManager em) {
        this.em = em;
    }

    @Override public String reportCode() { return "INV_STOCK"; }
    @Override public String displayName() { return "Inventory Stock Report"; }
    @Override public List<String> requiredAuthority() { return List.of("REPORT_VIEW"); }
    @Override public List<String> columnHeaders() {
        return List.of("Material Code", "Material Name", "Type", "UOM",
                "Available Qty", "Reserved Qty", "Total Value (INR)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runQuery(ReportRequest req) {
        String sql = """
                SELECT m.material_code, m.name, m.material_type, m.uom,
                       COALESCE(SUM(CASE WHEN b.stock_status='AVAILABLE' THEN b.qty_on_hand - b.qty_reserved ELSE 0 END), 0) AS available_qty,
                       COALESCE(SUM(b.qty_reserved), 0) AS reserved_qty,
                       COALESCE(SUM(b.qty_on_hand * m.standard_cost), 0) AS total_value
                FROM ims.materials m
                LEFT JOIN ims.inventory_balances b ON b.material_id = m.id
                WHERE m.active = true
                GROUP BY m.id, m.material_code, m.name, m.material_type, m.uom
                ORDER BY m.material_code
                LIMIT :size OFFSET :offset
                """;
        var query = em.createNativeQuery(sql);
        query.setParameter("size", req.size());
        query.setParameter("offset", (long) req.page() * req.size());
        return ReportQueryHelper.toMaps(query.getResultList(), columnHeaders());
    }
}

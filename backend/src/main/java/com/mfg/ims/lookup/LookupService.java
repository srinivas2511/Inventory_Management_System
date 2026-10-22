package com.mfg.ims.lookup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.lookup.LookupDtos.ActiveBom;
import com.mfg.ims.lookup.LookupDtos.DispatchableItem;
import com.mfg.ims.lookup.LookupDtos.DispatchableOrder;
import com.mfg.ims.lookup.LookupDtos.FgStock;
import com.mfg.ims.lookup.LookupDtos.InvoiceableReceipt;
import com.mfg.ims.lookup.LookupDtos.ItemOption;
import com.mfg.ims.lookup.LookupDtos.LocationOption;
import com.mfg.ims.lookup.LookupDtos.MaterialStock;
import com.mfg.ims.lookup.LookupDtos.Option;
import com.mfg.ims.lookup.LookupDtos.PickableOrder;
import com.mfg.ims.lookup.LookupDtos.ReceivablePo;
import com.mfg.ims.lookup.LookupDtos.ReceivablePoItem;

/**
 * Small, read-only lists that feed the dropdowns of the create forms. They exist because the master-data list
 * endpoints need the master-data permissions, which a purchaser, planner or dispatcher does not hold.
 */
@Service
@Transactional(readOnly = true)
public class LookupService {

    private static final int LIMIT = 500;

    private final NamedParameterJdbcTemplate jdbc;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public LookupService(NamedParameterJdbcTemplate jdbc, com.mfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.jdbc = jdbc;
    }

    public List<Option> suppliers(String q) {
        return options("SELECT id, supplier_code AS code, name FROM suppliers WHERE status = 'ACTIVE'",
                "supplier_code", q);
    }

    public List<Option> customers(String q) {
        return options("SELECT id, customer_code AS code, name FROM customers WHERE status = 'ACTIVE'",
                "customer_code", q);
    }

    public List<Option> warehouses(String q) {
        return options("SELECT id, warehouse_code AS code, name FROM warehouses WHERE active = TRUE"
                        + " AND (:scoped = FALSE OR plant_id IN (:plantIds))",
                "warehouse_code", q);
    }

    /** The plants the current user may create documents in. */
    public List<Option> plants() {
        return jdbc.query("""
                SELECT id, plant_code AS code, name FROM plants
                 WHERE active = TRUE AND (:scoped = FALSE OR id IN (:plantIds))
                 ORDER BY id
                """, scopeParams(),
                (rs, i) -> new Option(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    public List<ItemOption> materials(String q) {
        return items("SELECT id, material_code AS code, name, uom FROM materials WHERE active = TRUE",
                "material_code", q);
    }

    public List<ItemOption> products(String q) {
        return items("SELECT id, product_code AS code, name, uom FROM products WHERE active = TRUE",
                "product_code", q);
    }

    public List<LocationOption> locations(Long warehouseId) {
        return jdbc.query("""
                SELECT id, warehouse_id, location_code, name, location_type FROM warehouse_locations
                 WHERE active = TRUE AND (:wh = 0 OR warehouse_id = :wh)
                   AND (:whScoped = FALSE OR warehouse_id IN (:whIds))
                 ORDER BY warehouse_id, location_code LIMIT 1000
                """, scopeParams().addValue("wh", warehouseId == null ? 0L : warehouseId),
                (rs, i) -> new LocationOption(rs.getLong("id"), rs.getLong("warehouse_id"),
                        rs.getString("location_code"), rs.getString("name"), rs.getString("location_type")));
    }

    public Optional<ActiveBom> activeBom(Long productId) {
        return jdbc.query("""
                SELECT id, bom_number, revision, base_quantity FROM bom
                 WHERE product_id = :p AND status = 'ACTIVE'
                """, new MapSqlParameterSource("p", productId),
                (rs, i) -> new ActiveBom(rs.getLong("id"), rs.getString("bom_number"), rs.getString("revision"),
                        rs.getBigDecimal("base_quantity"))).stream().findFirst();
    }

    /** Purchase orders that can still be received against, with the open quantity per line. */
    public List<ReceivablePo> receivablePurchaseOrders() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT po.id AS po_id, po.po_number, po.supplier_id, s.name AS supplier_name, po.status,
                       i.id AS item_id, i.line_no, i.material_id, m.material_code, m.name AS material_name, m.uom,
                       i.ordered_qty, i.received_qty
                  FROM purchase_orders po
                  JOIN suppliers s ON s.id = po.supplier_id
                  JOIN purchase_order_items i ON i.purchase_order_id = po.id
                  JOIN materials m ON m.id = i.material_id
                 WHERE po.status IN ('SENT_TO_SUPPLIER', 'PARTIALLY_RECEIVED') AND i.ordered_qty > i.received_qty
                   AND (:scoped = FALSE OR po.plant_id IN (:plantIds))
                 ORDER BY po.id DESC, i.line_no LIMIT 2000
                """, scopeParams());
        Map<Long, List<Map<String, Object>>> byPo = rows.stream().collect(
                Collectors.groupingBy(r -> ((Number) r.get("po_id")).longValue(), LinkedHashMap::new,
                        Collectors.toList()));
        List<ReceivablePo> result = new ArrayList<>();
        byPo.forEach((poId, lines) -> {
            Map<String, Object> first = lines.get(0);
            List<ReceivablePoItem> items = lines.stream().map(r -> {
                BigDecimal ordered = (BigDecimal) r.get("ordered_qty");
                BigDecimal received = (BigDecimal) r.get("received_qty");
                return new ReceivablePoItem(((Number) r.get("item_id")).longValue(),
                        ((Number) r.get("line_no")).intValue(), ((Number) r.get("material_id")).longValue(),
                        (String) r.get("material_code"), (String) r.get("material_name"), (String) r.get("uom"),
                        ordered, received, ordered.subtract(received));
            }).toList();
            result.add(new ReceivablePo(poId, (String) first.get("po_number"),
                    ((Number) first.get("supplier_id")).longValue(), (String) first.get("supplier_name"),
                    (String) first.get("status"), items));
        });
        return result;
    }

    /** Finished goods that can be reserved or dispatched: released stock per batch and location. */
    /**
     * Posted goods receipts that still have value open to invoice, optionally for one supplier. The received value
     * uses the same per-line rounding as the three-way match (quantity x PO rate, 4 dp) and what is already claimed
     * is what the match counts: invoices that are not cancelled and not in exception.
     */
    public List<InvoiceableReceipt> invoiceableReceipts(Long supplierId) {
        List<InvoiceableReceipt> all = jdbc.query("""
                SELECT gr.id, gr.gr_number, gr.receipt_date, po.po_number, po.supplier_id,
                       COALESCE(SUM(ROUND(gi.received_qty * pi.rate, 4)), 0) AS received_value,
                       COALESCE(SUM(ROUND(ROUND(gi.received_qty * pi.rate, 4) * pi.tax_pct / 100, 4)), 0) AS received_tax,
                       (SELECT COALESCE(SUM(a.match_cleared_amount), 0)
                          FROM ap_invoices a
                         WHERE a.goods_receipt_id = gr.id
                           AND a.status <> 'CANCELLED'
                           AND a.match_status IN ('MATCHED', 'OVERRIDDEN', 'NOT_REQUIRED')) AS invoiced_value
                  FROM goods_receipts gr
                  JOIN purchase_orders po ON po.id = gr.purchase_order_id
                  JOIN goods_receipt_items gi ON gi.goods_receipt_id = gr.id
                  JOIN purchase_order_items pi ON pi.id = gi.po_item_id
                 WHERE gr.status = 'POSTED'
                   AND (:s = 0 OR po.supplier_id = :s)
                   AND (:scoped = FALSE OR gr.plant_id IN (:plantIds))
                 GROUP BY gr.id, gr.gr_number, gr.receipt_date, po.po_number, po.supplier_id
                 ORDER BY gr.receipt_date DESC, gr.id DESC
                 LIMIT 1000
                """, scopeParams().addValue("s", supplierId == null ? 0L : supplierId),
                (rs, i) -> {
                    BigDecimal value = rs.getBigDecimal("received_value");
                    BigDecimal invoiced = rs.getBigDecimal("invoiced_value");
                    BigDecimal open = value.subtract(invoiced).max(BigDecimal.ZERO);
                    return new InvoiceableReceipt(rs.getLong("id"), rs.getString("gr_number"),
                            rs.getDate("receipt_date").toLocalDate(), rs.getString("po_number"),
                            rs.getLong("supplier_id"), value, rs.getBigDecimal("received_tax"), invoiced, open);
                });
        return all.stream().filter(r -> r.openValue().signum() > 0).toList();
    }

    public List<FgStock> finishedGoodsStock(Long productId) {
        return jdbc.query("""
                SELECT b.fg_batch_id, f.batch_number, b.product_id, b.warehouse_id, b.location_id,
                       l.location_code, b.qty_on_hand, b.qty_reserved
                  FROM inventory_balances b
                  JOIN fg_batches f ON f.id = b.fg_batch_id
                  JOIN warehouse_locations l ON l.id = b.location_id
                 WHERE b.item_type = 'PRODUCT' AND b.stock_status = 'AVAILABLE' AND b.qty_on_hand > 0
                   AND f.quality_status IN ('APPROVED', 'PARTIALLY_APPROVED')
                   AND (:p = 0 OR b.product_id = :p)
                   AND (:whScoped = FALSE OR b.warehouse_id IN (:whIds))
                 ORDER BY f.produced_on, b.fg_batch_id, b.location_id LIMIT 1000
                """, scopeParams().addValue("p", productId == null ? 0L : productId),
                (rs, i) -> new FgStock(rs.getLong("fg_batch_id"), rs.getString("batch_number"),
                        rs.getLong("product_id"), rs.getLong("warehouse_id"), rs.getLong("location_id"),
                        rs.getString("location_code"), rs.getBigDecimal("qty_on_hand"),
                        rs.getBigDecimal("qty_reserved"),
                        rs.getBigDecimal("qty_on_hand").subtract(rs.getBigDecimal("qty_reserved"))));
    }

    /** Raw material the issue allocator can draw from, in the order it draws: earliest expiry first, then oldest receipt. */
    public List<MaterialStock> materialStock(Long materialId, Long warehouseId) {
        return jdbc.query("""
                SELECT b.material_batch_id, mb.batch_number, mb.expiry_date, mb.received_date, b.location_id,
                       l.location_code, b.qty_on_hand, b.qty_reserved
                  FROM inventory_balances b
                  JOIN material_batches mb ON mb.id = b.material_batch_id
                  JOIN warehouse_locations l ON l.id = b.location_id
                 WHERE b.item_type = 'MATERIAL' AND b.stock_status = 'AVAILABLE' AND mb.status = 'AVAILABLE'
                   AND b.material_id = :m AND b.warehouse_id = :w AND b.qty_on_hand - b.qty_reserved > 0
                   AND (:whScoped = FALSE OR b.warehouse_id IN (:whIds))
                 ORDER BY mb.expiry_date NULLS LAST, mb.received_date, mb.id, b.location_id LIMIT 500
                """, scopeParams().addValue("m", materialId).addValue("w", warehouseId),
                (rs, i) -> new MaterialStock(rs.getLong("material_batch_id"), rs.getString("batch_number"),
                        rs.getObject("expiry_date", java.time.LocalDate.class),
                        rs.getObject("received_date", java.time.LocalDate.class),
                        rs.getLong("location_id"), rs.getString("location_code"),
                        rs.getBigDecimal("qty_on_hand"), rs.getBigDecimal("qty_reserved"),
                        rs.getBigDecimal("qty_on_hand").subtract(rs.getBigDecimal("qty_reserved"))));
    }

    /** Approved sales orders that still have quantity to dispatch. */
    public List<DispatchableOrder> dispatchableOrders() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT so.id AS order_id, so.order_number, so.customer_id, c.name AS customer_name,
                       so.delivery_address, so.status, i.id AS item_id, i.line_no, i.product_id, p.product_code,
                       p.name AS product_name, i.ordered_qty, i.dispatched_qty, i.unit_price
                  FROM sales_orders so
                  JOIN customers c ON c.id = so.customer_id
                  JOIN sales_order_items i ON i.sales_order_id = so.id
                  JOIN products p ON p.id = i.product_id
                 WHERE so.status IN ('APPROVED', 'ALLOCATED', 'PARTIALLY_DISPATCHED')
                   AND i.ordered_qty > i.dispatched_qty
                   AND (:scoped = FALSE OR so.plant_id IN (:plantIds))
                 ORDER BY so.id DESC, i.line_no LIMIT 2000
                """, scopeParams());
        Map<Long, List<Map<String, Object>>> byOrder = rows.stream().collect(
                Collectors.groupingBy(r -> ((Number) r.get("order_id")).longValue(), LinkedHashMap::new,
                        Collectors.toList()));
        List<DispatchableOrder> result = new ArrayList<>();
        byOrder.forEach((orderId, lines) -> {
            Map<String, Object> first = lines.get(0);
            List<DispatchableItem> items = lines.stream().map(r -> {
                BigDecimal ordered = (BigDecimal) r.get("ordered_qty");
                BigDecimal dispatched = (BigDecimal) r.get("dispatched_qty");
                return new DispatchableItem(((Number) r.get("item_id")).longValue(),
                        ((Number) r.get("line_no")).intValue(), ((Number) r.get("product_id")).longValue(),
                        (String) r.get("product_code"), (String) r.get("product_name"), ordered, dispatched,
                        ordered.subtract(dispatched), (BigDecimal) r.get("unit_price"));
            }).toList();
            result.add(new DispatchableOrder(orderId, (String) first.get("order_number"),
                    ((Number) first.get("customer_id")).longValue(), (String) first.get("customer_name"),
                    (String) first.get("delivery_address"), (String) first.get("status"), items));
        });
        return result;
    }

    /** Approved sales orders, newest first, for the pick wave planner. */
    public List<PickableOrder> pickableOrders() {
        return jdbc.query("""
                SELECT so.id, so.order_number, c.name AS customer_name
                  FROM sales_orders so
                  JOIN customers c ON c.id = so.customer_id
                 WHERE so.status = 'APPROVED'
                   AND (:scoped = FALSE OR so.plant_id IN (:plantIds))
                 ORDER BY so.id DESC
                 LIMIT\s""" + LIMIT,
                scopeParams(),
                (rs, i) -> new PickableOrder(rs.getLong("id"), rs.getString("order_number"), rs.getString("customer_name")));
    }

    private List<Option> options(String select, String codeColumn, String q) {
        return jdbc.query(select + filter(codeColumn) + " ORDER BY " + codeColumn + " LIMIT " + LIMIT,
                scopeParams().addValue("q", SearchText.normalise(q)),
                (rs, i) -> new Option(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    private List<ItemOption> items(String select, String codeColumn, String q) {
        return jdbc.query(select + filter(codeColumn) + " ORDER BY " + codeColumn + " LIMIT " + LIMIT,
                scopeParams().addValue("q", SearchText.normalise(q)),
                (rs, i) -> new ItemOption(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("uom")));
    }

    /** Plant parameters every query may use: documents by plant, stock by the warehouses of the user's plants. */
    private MapSqlParameterSource scopeParams() {
        PlantScope.Filter plants = plantScope.filter();
        PlantScope.Filter warehouses = plantScope.warehouseFilter();
        return new MapSqlParameterSource()
                .addValue("scoped", plants.scoped())
                .addValue("plantIds", plants.plantIds())
                .addValue("whScoped", warehouses.scoped())
                .addValue("whIds", warehouses.plantIds());
    }

    private static String filter(String codeColumn) {
        return " AND (:q = '' OR LOWER(" + codeColumn + ") LIKE LOWER(CONCAT('%', :q, '%'))"
                + " OR LOWER(name) LIKE LOWER(CONCAT('%', :q, '%')))";
    }
}

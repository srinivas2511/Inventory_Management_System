package com.springmfg.ims.mrp.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.springmfg.ims.mrp.engine.MrpModel.BomInfo;
import com.springmfg.ims.mrp.engine.MrpModel.BomLine;
import com.springmfg.ims.mrp.engine.MrpModel.Demand;
import com.springmfg.ims.mrp.engine.MrpModel.ItemKey;
import com.springmfg.ims.mrp.engine.MrpModel.ItemType;
import com.springmfg.ims.mrp.engine.MrpModel.LotParams;
import com.springmfg.ims.mrp.engine.MrpModel.MaterialPlan;
import com.springmfg.ims.mrp.engine.MrpModel.MrpInput;
import com.springmfg.ims.mrp.engine.MrpModel.ProductPlan;
import com.springmfg.ims.mrp.engine.MrpModel.ProposalDraft;
import com.springmfg.ims.mrp.engine.MrpModel.RoutingStep;
import com.springmfg.ims.mrp.engine.MrpModel.Supply;
import com.springmfg.ims.mrp.engine.MrpModel.SupplyKind;

/**
 * Read-only SQL that assembles the MRP engine input (demand, supply, planning data), plus the bulk proposal
 * insert. MRP only reads stock balances; every stock write stays in InventoryPostingService.
 */
@Repository
public class MrpSourceDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MrpSourceDataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MrpInput load(LocalDate today, LocalDate horizonEnd, boolean includeSafetyStock,
                         int defaultPurchaseLead, int defaultProductionLead, BigDecimal machineHoursPerWeek) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("today", Date.valueOf(today))
                .addValue("end", Date.valueOf(horizonEnd))
                .addValue("purchaseLead", defaultPurchaseLead);

        List<Demand> demands = new ArrayList<>();
        demands.addAll(salesDemand(p));
        demands.addAll(forecastDemand(p));
        demands.addAll(productionMaterialDemand(p));
        if (includeSafetyStock) {
            demands.addAll(safetyDemand(today));
        }

        List<Supply> supplies = new ArrayList<>();
        supplies.addAll(stockSupply(today));
        supplies.addAll(openProductionSupply(p));
        supplies.addAll(openPoSupply(p));
        supplies.addAll(openPrSupply(p));

        Set<Long> productIds = new HashSet<>();
        Set<Long> materialIds = new HashSet<>();
        for (Demand d : demands) {
            (d.item().type() == ItemType.PRODUCT ? productIds : materialIds).add(d.item().id());
        }
        Map<Long, ProductPlan> products = productPlans(productIds, defaultProductionLead, materialIds);
        Map<Long, MaterialPlan> materials = materialPlans(materialIds, defaultPurchaseLead);
        return new MrpInput(today, horizonEnd, demands, supplies, products, materials, machineHoursPerWeek);
    }

    // ── demand ────────────────────────────────────────────────────────────────

    private List<Demand> salesDemand(SqlParameterSource p) {
        return jdbc.query("""
                SELECT soi.product_id, so.id AS so_id, (soi.ordered_qty - soi.dispatched_qty) AS qty,
                       COALESCE(so.required_date, CAST(:today AS date)) AS need_date
                  FROM ims.sales_order_items soi
                  JOIN ims.sales_orders so ON so.id = soi.sales_order_id
                 WHERE so.status IN ('ORDERED','APPROVED','ALLOCATED','PARTIALLY_DISPATCHED')
                   AND soi.ordered_qty > soi.dispatched_qty
                   AND COALESCE(so.required_date, CAST(:today AS date)) <= :end
                """, p, (rs, i) -> new Demand(new ItemKey(ItemType.PRODUCT, rs.getLong("product_id")),
                rs.getDate("need_date").toLocalDate(), rs.getBigDecimal("qty"), "SALES_ORDER", rs.getLong("so_id")));
    }

    private List<Demand> forecastDemand(SqlParameterSource p) {
        return jdbc.query("""
                SELECT id, product_id, target_date, quantity
                  FROM ims.demand_forecasts
                 WHERE target_date BETWEEN :today AND :end
                """, p, (rs, i) -> new Demand(new ItemKey(ItemType.PRODUCT, rs.getLong("product_id")),
                rs.getDate("target_date").toLocalDate(), rs.getBigDecimal("quantity"), "FORECAST", rs.getLong("id")));
    }

    /**
     * Material demand of existing production orders. Released orders carry a material snapshot (planned - issued);
     * DRAFT/APPROVED orders have none yet, so their BOM is exploded here. Without this a converted production
     * proposal would drop out of the plan until release.
     */
    private List<Demand> productionMaterialDemand(SqlParameterSource p) {
        List<Demand> demands = new ArrayList<>(jdbc.query("""
                SELECT pom.material_id, po.id AS po_id, (pom.planned_qty - pom.issued_qty) AS qty,
                       COALESCE(po.start_date, CAST(:today AS date)) AS need_date
                  FROM ims.production_order_materials pom
                  JOIN ims.production_orders po ON po.id = pom.production_order_id
                 WHERE po.status IN ('RELEASED','IN_PROGRESS')
                   AND pom.planned_qty > pom.issued_qty
                   AND COALESCE(po.start_date, CAST(:today AS date)) <= :end
                """, p, (rs, i) -> new Demand(new ItemKey(ItemType.MATERIAL, rs.getLong("material_id")),
                rs.getDate("need_date").toLocalDate(), rs.getBigDecimal("qty"), "PRODUCTION_ORDER", rs.getLong("po_id"))));
        demands.addAll(jdbc.query("""
                SELECT bi.material_id, po.id AS po_id,
                       CEIL(po.planned_qty * bi.required_qty / b.base_quantity * (1 + bi.scrap_pct / 100) * 1000) / 1000 AS qty,
                       COALESCE(po.start_date, CAST(:today AS date)) AS need_date
                  FROM ims.production_orders po
                  JOIN ims.bom b ON b.id = po.bom_id
                  JOIN ims.bom_items bi ON bi.bom_id = b.id AND bi.item_kind = 'MATERIAL'
                       AND bi.material_id IS NOT NULL AND bi.required_qty IS NOT NULL
                 WHERE po.status IN ('DRAFT','APPROVED')
                   AND COALESCE(po.start_date, CAST(:today AS date)) <= :end
                """, p, (rs, i) -> new Demand(new ItemKey(ItemType.MATERIAL, rs.getLong("material_id")),
                rs.getDate("need_date").toLocalDate(), rs.getBigDecimal("qty"), "PRODUCTION_ORDER", rs.getLong("po_id"))));
        return demands;
    }

    /** Safety stock: materials.min_stock and products.reorder_level, held as a requirement dated today. */
    private List<Demand> safetyDemand(LocalDate today) {
        List<Demand> out = new ArrayList<>();
        out.addAll(jdbc.query("SELECT id, min_stock AS qty FROM ims.materials WHERE active AND min_stock > 0",
                (rs, i) -> new Demand(new ItemKey(ItemType.MATERIAL, rs.getLong("id")), today,
                        rs.getBigDecimal("qty"), "SAFETY_STOCK", null)));
        out.addAll(jdbc.query("SELECT id, reorder_level AS qty FROM ims.products WHERE active AND reorder_level > 0",
                (rs, i) -> new Demand(new ItemKey(ItemType.PRODUCT, rs.getLong("id")), today,
                        rs.getBigDecimal("qty"), "SAFETY_STOCK", null)));
        return out;
    }

    // ── supply ────────────────────────────────────────────────────────────────

    /** Finished goods are not netted against their own reservations, which belong to the demand being planned. */
    private List<Supply> stockSupply(LocalDate today) {
        return jdbc.query("""
                SELECT item_type, material_id, product_id,
                       SUM(qty_on_hand - CASE WHEN item_type = 'MATERIAL' THEN qty_reserved ELSE 0 END) AS qty
                  FROM ims.inventory_balances
                 WHERE stock_status = 'AVAILABLE'
                 GROUP BY item_type, material_id, product_id
                HAVING SUM(qty_on_hand - CASE WHEN item_type = 'MATERIAL' THEN qty_reserved ELSE 0 END) > 0
                """, (rs, i) -> {
            boolean material = "MATERIAL".equals(rs.getString("item_type"));
            long id = material ? rs.getLong("material_id") : rs.getLong("product_id");
            return new Supply(new ItemKey(material ? ItemType.MATERIAL : ItemType.PRODUCT, id), today,
                    rs.getBigDecimal("qty"), SupplyKind.STOCK);
        });
    }

    private List<Supply> openProductionSupply(SqlParameterSource p) {
        return jdbc.query("""
                SELECT product_id, (planned_qty - good_qty) AS qty,
                       COALESCE(expected_completion_date, start_date, CAST(:today AS date)) AS arrival
                  FROM ims.production_orders
                 WHERE status IN ('DRAFT','APPROVED','RELEASED','IN_PROGRESS') AND planned_qty > good_qty
                """, p,
                (rs, i) -> new Supply(new ItemKey(ItemType.PRODUCT, rs.getLong("product_id")),
                        rs.getDate("arrival").toLocalDate(), rs.getBigDecimal("qty"), SupplyKind.OPEN_PRODUCTION));
    }

    private List<Supply> openPoSupply(SqlParameterSource p) {
        return jdbc.query("""
                SELECT poi.material_id, (poi.ordered_qty - poi.received_qty) AS qty,
                       COALESCE(po.expected_delivery_date, po.po_date + CAST(:purchaseLead AS integer)) AS arrival
                  FROM ims.purchase_order_items poi
                  JOIN ims.purchase_orders po ON po.id = poi.purchase_order_id
                 WHERE po.status IN ('SUBMITTED','APPROVED','SENT_TO_SUPPLIER','PARTIALLY_RECEIVED')
                   AND poi.ordered_qty > poi.received_qty
                """, p, (rs, i) -> new Supply(new ItemKey(ItemType.MATERIAL, rs.getLong("material_id")),
                rs.getDate("arrival").toLocalDate(), rs.getBigDecimal("qty"), SupplyKind.OPEN_PO));
    }

    private List<Supply> openPrSupply(SqlParameterSource p) {
        return jdbc.query("""
                SELECT material_id, requested_qty AS qty,
                       COALESCE(needed_by, CAST(:today AS date) + CAST(:purchaseLead AS integer)) AS arrival
                  FROM ims.purchase_requisitions
                 WHERE status = 'OPEN'
                """, p, (rs, i) -> new Supply(new ItemKey(ItemType.MATERIAL, rs.getLong("material_id")),
                rs.getDate("arrival").toLocalDate(), rs.getBigDecimal("qty"), SupplyKind.OPEN_PR));
    }

    // ── planning data ─────────────────────────────────────────────────────────

    private record Param(String strategy, LotParams lot, Integer leadDays) {}

    private Map<Long, ProductPlan> productPlans(Set<Long> productIds, int defaultLead, Set<Long> materialIds) {
        Map<Long, ProductPlan> plans = new HashMap<>();
        if (productIds.isEmpty()) {
            return plans;
        }
        MapSqlParameterSource ids = new MapSqlParameterSource("ids", productIds);
        Map<Long, String> uoms = new HashMap<>();
        jdbc.query("SELECT id, uom FROM ims.products WHERE active AND id IN (:ids)", ids,
                (RowCallbackHandlerAdapter) rs -> uoms.put(rs.getLong("id"), rs.getString("uom")));

        Map<Long, Param> params = new HashMap<>();
        jdbc.query("""
                SELECT product_id, planning_strategy, lot_size_rule, fixed_lot_qty, min_lot_qty, max_lot_qty,
                       eoq_annual_demand, eoq_order_cost, eoq_holding_cost, lead_time_days
                  FROM ims.mrp_planning_params WHERE product_id IN (:ids)
                """, ids, (RowCallbackHandlerAdapter) rs -> params.put(rs.getLong("product_id"), param(rs)));

        Map<Long, BomInfo> boms = activeBoms(ids, materialIds);
        Map<Long, List<RoutingStep>> routing = new HashMap<>();
        jdbc.query("""
                SELECT product_id, machine_id, standard_time_min, setup_time_min
                  FROM ims.product_routing WHERE active AND product_id IN (:ids) ORDER BY product_id, sequence_no
                """, ids, (RowCallbackHandlerAdapter) rs -> routing
                .computeIfAbsent(rs.getLong("product_id"), k -> new ArrayList<>())
                .add(new RoutingStep(rs.getObject("machine_id") == null ? null : rs.getLong("machine_id"),
                        nz(rs.getBigDecimal("standard_time_min")), nz(rs.getBigDecimal("setup_time_min")))));

        uoms.forEach((id, uom) -> {
            Param prm = params.get(id);
            plans.put(id, new ProductPlan(id, uom == null ? "PCS" : uom,
                    prm != null && "MTO".equals(prm.strategy()),
                    prm == null ? LotParams.exact() : prm.lot(),
                    prm != null && prm.leadDays() != null ? prm.leadDays() : defaultLead,
                    boms.get(id), routing.getOrDefault(id, List.of())));
        });
        return plans;
    }

    /** Fills {@code materialIds} with every component of the active BOMs so they get planning data. */
    private Map<Long, BomInfo> activeBoms(MapSqlParameterSource ids, Set<Long> materialIds) {
        Map<Long, Long> bomByProduct = new HashMap<>();
        Map<Long, BigDecimal> baseQty = new HashMap<>();
        jdbc.query("SELECT id, product_id, base_quantity FROM ims.bom WHERE status = 'ACTIVE' AND product_id IN (:ids)",
                ids, (RowCallbackHandlerAdapter) rs -> {
                    bomByProduct.put(rs.getLong("product_id"), rs.getLong("id"));
                    baseQty.put(rs.getLong("id"), rs.getBigDecimal("base_quantity"));
                });
        Map<Long, List<BomLine>> lines = new HashMap<>();
        if (!bomByProduct.isEmpty()) {
            jdbc.query("""
                    SELECT bom_id, material_id, required_qty, scrap_pct FROM ims.bom_items
                     WHERE item_kind = 'MATERIAL' AND material_id IS NOT NULL AND required_qty IS NOT NULL
                       AND bom_id IN (:bomIds) ORDER BY bom_id, line_no
                    """, new MapSqlParameterSource("bomIds", bomByProduct.values()),
                    (RowCallbackHandlerAdapter) rs -> {
                        long materialId = rs.getLong("material_id");
                        materialIds.add(materialId);
                        lines.computeIfAbsent(rs.getLong("bom_id"), k -> new ArrayList<>())
                                .add(new BomLine(materialId, rs.getBigDecimal("required_qty"), nz(rs.getBigDecimal("scrap_pct"))));
                    });
        }
        Map<Long, BomInfo> boms = new HashMap<>();
        bomByProduct.forEach((productId, bomId) -> boms.put(productId,
                new BomInfo(bomId, baseQty.get(bomId), lines.getOrDefault(bomId, List.of()))));
        return boms;
    }

    private Map<Long, MaterialPlan> materialPlans(Set<Long> materialIds, int defaultLead) {
        Map<Long, MaterialPlan> plans = new HashMap<>();
        if (materialIds.isEmpty()) {
            return plans;
        }
        MapSqlParameterSource ids = new MapSqlParameterSource("ids", materialIds);
        Map<Long, Param> params = new HashMap<>();
        jdbc.query("""
                SELECT material_id, planning_strategy, lot_size_rule, fixed_lot_qty, min_lot_qty, max_lot_qty,
                       eoq_annual_demand, eoq_order_cost, eoq_holding_cost, lead_time_days
                  FROM ims.mrp_planning_params WHERE material_id IN (:ids)
                """, ids, (RowCallbackHandlerAdapter) rs -> params.put(rs.getLong("material_id"), param(rs)));
        jdbc.query("""
                SELECT m.id, m.uom, s.lead_time_days AS supplier_lead
                  FROM ims.materials m LEFT JOIN ims.suppliers s ON s.id = m.preferred_supplier_id
                 WHERE m.active AND m.id IN (:ids)
                """, ids, (RowCallbackHandlerAdapter) rs -> {
            long id = rs.getLong("id");
            Param prm = params.get(id);
            int supplierLead = rs.getObject("supplier_lead") == null ? defaultLead : rs.getInt("supplier_lead");
            int lead = prm != null && prm.leadDays() != null ? prm.leadDays() : supplierLead;
            plans.put(id, new MaterialPlan(id, rs.getString("uom"), prm == null ? LotParams.exact() : prm.lot(), lead));
        });
        return plans;
    }

    private static Param param(ResultSet rs) throws SQLException {
        LotParams lot = new LotParams(rs.getString("lot_size_rule"), rs.getBigDecimal("fixed_lot_qty"),
                rs.getBigDecimal("min_lot_qty"), rs.getBigDecimal("max_lot_qty"), rs.getBigDecimal("eoq_annual_demand"),
                rs.getBigDecimal("eoq_order_cost"), rs.getBigDecimal("eoq_holding_cost"));
        return new Param(rs.getString("planning_strategy"), lot,
                rs.getObject("lead_time_days") == null ? null : rs.getInt("lead_time_days"));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @FunctionalInterface
    private interface RowCallbackHandlerAdapter extends org.springframework.jdbc.core.RowCallbackHandler {
        void handle(ResultSet rs) throws SQLException;

        @Override
        default void processRow(ResultSet rs) throws SQLException {
            handle(rs);
        }
    }

    // ── persistence ───────────────────────────────────────────────────────────

    private static final int INSERT_CHUNK = 500;
    private static final String ROW_PLACEHOLDERS = "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)";

    /** Multi-row inserts: one round trip per chunk instead of one per proposal. */
    public void insertProposals(Long runId, Long userId, List<ProposalDraft> drafts) {
        for (int from = 0; from < drafts.size(); from += INSERT_CHUNK) {
            List<ProposalDraft> chunk = drafts.subList(from, Math.min(from + INSERT_CHUNK, drafts.size()));
            StringBuilder sql = new StringBuilder("""
                    INSERT INTO ims.mrp_proposals (mrp_run_id, proposal_type, material_id, product_id, bom_id, uom,
                           required_qty, proposed_qty, need_date, proposed_order_date, lead_time_days, lead_time_adjusted,
                           source_demand_type, source_demand_id, lot_size_rule, machine_hours_required, capacity_overload,
                           status, created_by, updated_by)
                    VALUES\s""");
            List<Object> args = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                ProposalDraft d = chunk.get(i);
                sql.append(i == 0 ? "" : ", ").append(ROW_PLACEHOLDERS);
                args.add(runId);
                args.add(d.type());
                args.add(d.materialId());
                args.add(d.productId());
                args.add(d.bomId());
                args.add(d.uom());
                args.add(d.requiredQty());
                args.add(d.proposedQty());
                args.add(Date.valueOf(d.needDate()));
                args.add(Date.valueOf(d.orderDate()));
                args.add(d.leadDays());
                args.add(d.leadTimeAdjusted());
                args.add(d.sourceType());
                args.add(d.sourceId());
                args.add(d.lotRule());
                args.add(d.machineHours());
                args.add(d.capacityOverload());
                args.add(userId);
                args.add(userId);
            }
            jdbc.getJdbcTemplate().update(sql.toString(), args.toArray());
        }
    }

    /** Transaction-scoped advisory lock: only one MRP run may be planning at a time. */
    public boolean tryLockRun() {
        Boolean locked = jdbc.getJdbcTemplate()
                .queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('ims.mrp.run'))", Boolean.class);
        return Boolean.TRUE.equals(locked);
    }
}

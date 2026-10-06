package com.springmfg.ims.inventory.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only stock queries for the warehouse module. It never writes: every stock movement still goes through
 * {@link InventoryPostingService} (CLAUDE.md rule 1). Pending JPA writes are flushed first so a query inside a
 * transaction sees the stock posted earlier in the same transaction.
 */
@Service
public class StockLocationQueryService {

    /** One balance row with the batch ordering date used for FEFO/FIFO picking. */
    public record LocationStock(Long locationId, Long warehouseId, String itemType, Long materialId, Long productId,
                                Long batchId, String stockStatus, BigDecimal qtyOnHand, BigDecimal qtyReserved,
                                LocalDate pickDate) {
        public Long itemId() {
            return "MATERIAL".equals(itemType) ? materialId : productId;
        }

        public BigDecimal available() {
            return qtyOnHand.subtract(qtyReserved);
        }
    }

    /** Descriptive item data: code, name, unit of measure, material type (null for products). */
    public record ItemInfo(String code, String name, String uom, String materialType) {
    }

    private static final String STOCK_SELECT = """
            SELECT b.location_id, b.warehouse_id, b.item_type, b.material_id, b.product_id,
                   COALESCE(b.material_batch_id, b.fg_batch_id) AS batch_id, b.stock_status,
                   b.qty_on_hand, b.qty_reserved,
                   CASE WHEN b.item_type = 'MATERIAL' THEN COALESCE(mb.expiry_date, mb.received_date)
                        ELSE fb.produced_on END AS pick_date
            FROM ims.inventory_balances b
            LEFT JOIN ims.material_batches mb ON mb.id = b.material_batch_id
            LEFT JOIN ims.fg_batches fb ON fb.id = b.fg_batch_id
            """;

    private static final RowMapper<LocationStock> STOCK_MAPPER = StockLocationQueryService::mapStock;

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager em;

    public StockLocationQueryService(JdbcTemplate jdbcTemplate, EntityManager em) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.em = em;
    }

    private static LocationStock mapStock(ResultSet rs, int row) throws SQLException {
        java.sql.Date pick = rs.getDate("pick_date");
        return new LocationStock(rs.getLong("location_id"), rs.getLong("warehouse_id"), rs.getString("item_type"),
                (Long) rs.getObject("material_id"), (Long) rs.getObject("product_id"),
                (Long) rs.getObject("batch_id"), rs.getString("stock_status"), rs.getBigDecimal("qty_on_hand"),
                rs.getBigDecimal("qty_reserved"), pick == null ? null : pick.toLocalDate());
    }

    private void flush() {
        em.flush();
    }

    /** Stock with a positive on-hand quantity at the given ledger locations. */
    @Transactional(readOnly = true)
    public List<LocationStock> stockAtLocations(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return List.of();
        }
        flush();
        return jdbc.query(STOCK_SELECT + " WHERE b.qty_on_hand > 0 AND b.location_id IN (:ids)"
                        + " ORDER BY b.location_id, b.item_type, b.material_id, b.product_id, batch_id",
                new MapSqlParameterSource("ids", locationIds), STOCK_MAPPER);
    }

    /** Stock rows of one batch at one location, one per stock status (usually one). */
    @Transactional(readOnly = true)
    public List<LocationStock> stockOfBatchAt(Long locationId, String itemType, Long itemId, Long batchId) {
        flush();
        String itemCol = "MATERIAL".equals(itemType) ? "b.material_id" : "b.product_id";
        String batchCol = "MATERIAL".equals(itemType) ? "b.material_batch_id" : "b.fg_batch_id";
        return jdbc.query(STOCK_SELECT + " WHERE b.location_id = :loc AND b.item_type = :it AND " + itemCol
                        + " = :item AND COALESCE(" + batchCol + ", 0) = COALESCE(:batch, 0) AND b.qty_on_hand > 0",
                new MapSqlParameterSource("loc", locationId).addValue("it", itemType).addValue("item", itemId)
                        .addValue("batch", batchId), STOCK_MAPPER);
    }

    /** Stock rows of one batch at every location and in every stock status, with a positive on-hand quantity. */
    @Transactional(readOnly = true)
    public List<LocationStock> stockOfBatch(String itemType, Long itemId, Long batchId) {
        flush();
        String itemCol = "MATERIAL".equals(itemType) ? "b.material_id" : "b.product_id";
        String batchCol = "MATERIAL".equals(itemType) ? "b.material_batch_id" : "b.fg_batch_id";
        return jdbc.query(STOCK_SELECT + " WHERE b.item_type = :it AND " + itemCol + " = :item AND " + batchCol
                        + " = :batch AND b.qty_on_hand > 0 ORDER BY b.location_id, b.stock_status",
                new MapSqlParameterSource("it", itemType).addValue("item", itemId).addValue("batch", batchId),
                STOCK_MAPPER);
    }

    /**
     * Pickable stock of an item in first-expiry/first-in order: materials by expiry then receipt date, finished
     * goods by production date. Only released stock is returned (AVAILABLE for materials and products).
     */
    @Transactional(readOnly = true)
    public List<LocationStock> pickableStock(String itemType, Long itemId, Long warehouseId) {
        flush();
        String itemCol = "MATERIAL".equals(itemType) ? "b.material_id" : "b.product_id";
        String status = "AVAILABLE";
        return jdbc.query(STOCK_SELECT + " WHERE b.item_type = :it AND " + itemCol + " = :item AND b.stock_status = :st"
                        + " AND b.qty_on_hand > 0 AND (:wh IS NULL OR b.warehouse_id = :wh)"
                        + " ORDER BY pick_date NULLS LAST, batch_id, b.location_id",
                new MapSqlParameterSource("it", itemType).addValue("item", itemId).addValue("st", status)
                        .addValue("wh", warehouseId, java.sql.Types.BIGINT), STOCK_MAPPER);
    }

    /** All stock with a positive quantity in a warehouse (cycle-count snapshots). */
    @Transactional(readOnly = true)
    public List<LocationStock> stockInWarehouse(Long warehouseId) {
        flush();
        return jdbc.query(STOCK_SELECT + " WHERE b.warehouse_id = :wh AND b.qty_on_hand > 0"
                        + " ORDER BY b.location_id, b.item_type, b.material_id, b.product_id, batch_id",
                new MapSqlParameterSource("wh", warehouseId), STOCK_MAPPER);
    }

    /** Whether any positive stock sits at the location. */
    @Transactional(readOnly = true)
    public boolean hasStock(Long locationId) {
        flush();
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ims.inventory_balances WHERE location_id = :loc AND qty_on_hand > 0",
                new MapSqlParameterSource("loc", locationId), Integer.class);
        return n != null && n > 0;
    }

    /** The locations (from the given set) that hold positive stock. */
    @Transactional(readOnly = true)
    public java.util.Set<Long> occupiedLocations(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return java.util.Set.of();
        }
        flush();
        List<Long> ids = jdbc.queryForList("SELECT DISTINCT location_id FROM ims.inventory_balances"
                        + " WHERE qty_on_hand > 0 AND location_id IN (:ids)",
                new MapSqlParameterSource("ids", locationIds), Long.class);
        return new java.util.HashSet<>(ids);
    }

    /**
     * Weight of the stock at a location in kg. Products use their unit weight; materials count only when their
     * unit of measure is KG (the master data holds no other material weight).
     */
    @Transactional(readOnly = true)
    public BigDecimal locationWeightKg(Long locationId) {
        flush();
        BigDecimal w = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE
                         WHEN b.item_type = 'PRODUCT' THEN b.qty_on_hand * COALESCE(p.unit_weight_kg, 0)
                         WHEN UPPER(m.uom) = 'KG' THEN b.qty_on_hand
                         ELSE 0 END), 0)
                FROM ims.inventory_balances b
                LEFT JOIN ims.products p ON p.id = b.product_id
                LEFT JOIN ims.materials m ON m.id = b.material_id
                WHERE b.location_id = :loc AND b.qty_on_hand > 0
                """, new MapSqlParameterSource("loc", locationId), BigDecimal.class);
        return w == null ? BigDecimal.ZERO : w;
    }

    /** Weight of one unit of an item in kg (0 when unknown). */
    @Transactional(readOnly = true)
    public BigDecimal unitWeightKg(String itemType, Long itemId) {
        if ("PRODUCT".equals(itemType)) {
            List<BigDecimal> r = jdbc.queryForList("SELECT COALESCE(unit_weight_kg, 0) FROM ims.products WHERE id = :id",
                    new MapSqlParameterSource("id", itemId), BigDecimal.class);
            return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
        }
        List<String> uom = jdbc.queryForList("SELECT uom FROM ims.materials WHERE id = :id",
                new MapSqlParameterSource("id", itemId), String.class);
        return !uom.isEmpty() && "KG".equalsIgnoreCase(uom.get(0)) ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Optional<ItemInfo> itemInfo(String itemType, Long itemId) {
        String sql = "MATERIAL".equals(itemType)
                ? "SELECT material_code AS code, name, uom, material_type FROM ims.materials WHERE id = :id"
                : "SELECT product_code AS code, name, uom, NULL AS material_type FROM ims.products WHERE id = :id";
        List<ItemInfo> r = jdbc.query(sql, new MapSqlParameterSource("id", itemId),
                (rs, i) -> new ItemInfo(rs.getString("code"), rs.getString("name"), rs.getString("uom"),
                        rs.getString("material_type")));
        return r.stream().findFirst();
    }

    /** Batch number of a material or finished-goods batch. */
    @Transactional(readOnly = true)
    public Optional<String> batchNumber(String itemType, Long batchId) {
        if (batchId == null) {
            return Optional.empty();
        }
        String table = "MATERIAL".equals(itemType) ? "ims.material_batches" : "ims.fg_batches";
        return jdbc.queryForList("SELECT batch_number FROM " + table + " WHERE id = :id",
                new MapSqlParameterSource("id", batchId), String.class).stream().findFirst();
    }

    /** Status of a material batch (QUARANTINE, AVAILABLE, ...) or the quality status of a finished-goods batch. */
    @Transactional(readOnly = true)
    public Optional<String> batchStatus(String itemType, Long batchId) {
        if (batchId == null) {
            return Optional.empty();
        }
        String sql = "MATERIAL".equals(itemType)
                ? "SELECT status FROM ims.material_batches WHERE id = :id"
                : "SELECT quality_status FROM ims.fg_batches WHERE id = :id";
        return jdbc.queryForList(sql, new MapSqlParameterSource("id", batchId), String.class).stream().findFirst();
    }

    /**
     * Consumption value per item since a date, for ABC classification. Keys are "M:id" for materials (issues and
     * consumption) and "P:id" for products (dispatches).
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> consumptionValueSince(Instant since, Long warehouseId) {
        flush();
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                SELECT CASE WHEN item_type = 'MATERIAL' THEN 'M:' || material_id ELSE 'P:' || product_id END AS item_key,
                       SUM(ABS(quantity) * unit_cost) AS value
                FROM ims.inventory_transactions
                WHERE transaction_date >= :since AND warehouse_id = :wh
                  AND transaction_type IN ('MATERIAL_ISSUE', 'MATERIAL_CONSUMPTION', 'SALES_DISPATCH', 'MAINTENANCE_ISSUE')
                GROUP BY 1
                """, new MapSqlParameterSource("since", java.sql.Timestamp.from(since)).addValue("wh", warehouseId),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> out.put(rs.getString("item_key"),
                        rs.getBigDecimal("value")));
        return out;
    }

    /**
     * Value already taken out of work in progress as scrap for the given production outputs: the ledger value of
     * their PRODUCTION_REJECTION postings. Finished-goods costing nets it off the material cost so the same material
     * is never relieved from WIP twice.
     */
    @Transactional(readOnly = true)
    public BigDecimal scrapValueOfOutputs(Collection<Long> productionOutputIds) {
        if (productionOutputIds == null || productionOutputIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        flush();
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_cost), 0) FROM ims.inventory_transactions
                WHERE transaction_type = 'PRODUCTION_REJECTION' AND reference_type = 'PRODUCTION_OUTPUT'
                  AND reference_id IN (:ids)
                """, new MapSqlParameterSource("ids", productionOutputIds), BigDecimal.class);
        return value != null ? value : BigDecimal.ZERO;
    }
}

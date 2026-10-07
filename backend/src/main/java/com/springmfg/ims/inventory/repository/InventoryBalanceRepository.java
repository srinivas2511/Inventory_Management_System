package com.springmfg.ims.inventory.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.inventory.domain.InventoryBalance;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b WHERE b.warehouseId = :wh AND b.locationId = :loc AND b.itemType = :it " +
           "AND COALESCE(b.materialId,0) = COALESCE(:matId,0) AND COALESCE(b.productId,0) = COALESCE(:prodId,0) " +
           "AND COALESCE(b.materialBatchId,0) = COALESCE(:mbId,0) AND COALESCE(b.fgBatchId,0) = COALESCE(:fgId,0) " +
           "AND b.stockStatus = :status")
    Optional<InventoryBalance> lockBalance(
            @Param("wh") Long warehouseId, @Param("loc") Long locationId,
            @Param("it") String itemType,
            @Param("matId") Long materialId, @Param("prodId") Long productId,
            @Param("mbId") Long materialBatchId, @Param("fgId") Long fgBatchId,
            @Param("status") String stockStatus);

    @Query("SELECT b FROM InventoryBalance b WHERE b.warehouseId = :wh AND b.locationId = :loc AND b.itemType = :it " +
           "AND COALESCE(b.materialId,0) = COALESCE(:matId,0) AND COALESCE(b.productId,0) = COALESCE(:prodId,0) " +
           "AND COALESCE(b.materialBatchId,0) = COALESCE(:mbId,0) AND COALESCE(b.fgBatchId,0) = COALESCE(:fgId,0) " +
           "AND b.stockStatus = :status")
    Optional<InventoryBalance> findBalance(
            @Param("wh") Long warehouseId, @Param("loc") Long locationId,
            @Param("it") String itemType,
            @Param("matId") Long materialId, @Param("prodId") Long productId,
            @Param("mbId") Long materialBatchId, @Param("fgId") Long fgBatchId,
            @Param("status") String stockStatus);

    /** Every batch of an item held at one location and status: a count that names no batch covers all of them. */
    @Query("SELECT b FROM InventoryBalance b WHERE b.warehouseId = :wh AND b.locationId = :loc AND b.itemType = :it " +
           "AND COALESCE(b.materialId,0) = COALESCE(:matId,0) AND COALESCE(b.productId,0) = COALESCE(:prodId,0) " +
           "AND b.stockStatus = :status ORDER BY b.id")
    List<InventoryBalance> findAllForItemAtLocation(
            @Param("wh") Long warehouseId, @Param("loc") Long locationId,
            @Param("it") String itemType,
            @Param("matId") Long materialId, @Param("prodId") Long productId,
            @Param("status") String stockStatus);

    @Query("SELECT b FROM InventoryBalance b WHERE b.materialId = :matId AND b.stockStatus = 'AVAILABLE' AND b.qtyOnHand > b.qtyReserved ORDER BY b.id")
    List<InventoryBalance> findAvailableByMaterial(@Param("matId") Long materialId);

    @Query("SELECT b FROM InventoryBalance b WHERE b.materialId = :matId AND b.warehouseId = :wh AND b.stockStatus = 'AVAILABLE' AND b.qtyOnHand > b.qtyReserved ORDER BY b.id")
    List<InventoryBalance> findAvailableByMaterialAndWarehouse(@Param("matId") Long materialId, @Param("wh") Long warehouseId);

    @Query("SELECT b FROM InventoryBalance b WHERE b.productId = :prodId AND b.stockStatus = 'AVAILABLE' ORDER BY b.id")
    List<InventoryBalance> findAvailableByProduct(@Param("prodId") Long productId);

    @Query("SELECT SUM(b.qtyOnHand) FROM InventoryBalance b WHERE b.materialId = :matId AND b.stockStatus = :status")
    java.math.BigDecimal sumQtyByMaterialAndStatus(@Param("matId") Long materialId, @Param("status") String status);

    List<InventoryBalance> findByWarehouseId(Long warehouseId);

    @Query("SELECT b FROM InventoryBalance b WHERE b.materialId = :matId")
    List<InventoryBalance> findByMaterialId(@Param("matId") Long materialId);

    @Query("SELECT b FROM InventoryBalance b WHERE b.productId = :prodId")
    List<InventoryBalance> findByProductId(@Param("prodId") Long prodId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b WHERE b.fgBatchId = :fgId AND b.warehouseId = :wh AND b.locationId = :loc")
    Optional<InventoryBalance> lockByFgBatchAndLocation(
            @Param("fgId") Long fgBatchId,
            @Param("wh") Long warehouseId,
            @Param("loc") Long locationId);

    @Query(value = "SELECT COUNT(DISTINCT COALESCE(CAST(b.material_id AS TEXT), 'P' || b.product_id)) FROM ims.inventory_balances b WHERE b.qty_on_hand > 0", nativeQuery = true)
    long countItemsWithStock();

    @Query(value = "SELECT COALESCE(SUM(b.qty_on_hand * m.standard_cost), 0) FROM ims.inventory_balances b JOIN ims.materials m ON m.id = b.material_id WHERE b.qty_on_hand > 0", nativeQuery = true)
    java.math.BigDecimal sumInventoryValue();
}

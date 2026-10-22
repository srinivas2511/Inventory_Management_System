package com.mfg.ims.inventory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.common.search.QueryBounds;

import com.mfg.ims.inventory.domain.InventoryTransaction;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Optional<InventoryTransaction> findByTransactionNumber(String number);

    boolean existsByReversalOfId(Long txId);

    @Query("SELECT t FROM InventoryTransaction t WHERE " +
           "(:matId IS NULL OR t.materialId = :matId) AND " +
           "(:prodId IS NULL OR t.productId = :prodId) AND " +
           "(:wh IS NULL OR t.warehouseId = :wh) AND " +
           "(:type IS NULL OR t.transactionType = :type) AND " +
           "t.transactionDate >= :from AND " +
           "t.transactionDate <= :to AND " +
           "(:scoped = FALSE OR t.warehouseId IN :whIds) " +
           "ORDER BY t.transactionDate DESC")
    Page<InventoryTransaction> searchBounded(
            @Param("matId") Long materialId,
            @Param("prodId") Long productId,
            @Param("wh") Long warehouseId,
            @Param("type") String transactionType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("scoped") boolean scoped,
            @Param("whIds") java.util.Collection<Long> warehouseIds,
            Pageable pageable);

    default Page<InventoryTransaction> searchInWarehouses(Long materialId, Long productId, Long warehouseId,
            String transactionType, Instant from, Instant to, boolean scoped,
            java.util.Collection<Long> warehouseIds, Pageable pageable) {
        return searchBounded(materialId, productId, warehouseId, transactionType,
                QueryBounds.from(from), QueryBounds.to(to), scoped, warehouseIds, pageable);
    }

    default Page<InventoryTransaction> search(Long materialId, Long productId, Long warehouseId,
            String transactionType, Instant from, Instant to, Pageable pageable) {
        return searchInWarehouses(materialId, productId, warehouseId, transactionType, from, to, false,
                List.of(-1L), pageable);
    }

    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM InventoryTransaction t WHERE " +
           "t.materialId = :matId AND t.warehouseId = :wh AND t.locationId = :loc AND t.stockStatus = :status AND t.transactionDate <= :asOf")
    java.math.BigDecimal sumLedgerAsOf(
            @Param("matId") Long materialId, @Param("wh") Long warehouseId,
            @Param("loc") Long locationId, @Param("status") String stockStatus,
            @Param("asOf") Instant asOf);

    @Query("SELECT COALESCE(SUM(t.quantity), 0) FROM InventoryTransaction t WHERE " +
           "t.warehouseId = :wh AND t.locationId = :loc AND t.itemType = :it " +
           "AND COALESCE(t.materialId,0) = COALESCE(:matId,0) " +
           "AND COALESCE(t.productId,0) = COALESCE(:prodId,0) " +
           "AND COALESCE(t.materialBatchId,0) = COALESCE(:mbId,0) " +
           "AND COALESCE(t.fgBatchId,0) = COALESCE(:fgId,0) " +
           "AND t.stockStatus = :status AND t.transactionDate <= :asOf")
    java.math.BigDecimal sumBalanceLedger(
            @Param("wh") Long warehouseId, @Param("loc") Long locationId,
            @Param("it") String itemType,
            @Param("matId") Long materialId, @Param("prodId") Long productId,
            @Param("mbId") Long materialBatchId, @Param("fgId") Long fgBatchId,
            @Param("status") String stockStatus, @Param("asOf") Instant asOf);

    List<InventoryTransaction> findByReferenceTypeAndReferenceId(String refType, Long refId);

    List<InventoryTransaction> findByMaterialBatchId(Long batchId);
}

package com.mfg.ims.sales.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.sales.domain.SalesReservation;

public interface SalesReservationRepository extends JpaRepository<SalesReservation, Long> {

    List<SalesReservation> findBySalesOrderItemIdAndStatus(Long salesOrderItemId, String status);

    List<SalesReservation> findByFgBatchIdAndStatus(Long fgBatchId, String status);

    @Query("SELECT COALESCE(SUM(r.reservedQty), 0) FROM SalesReservation r WHERE r.fgBatchId = :batchId AND r.status = 'ACTIVE'")
    java.math.BigDecimal sumReservedQtyForBatch(@Param("batchId") Long fgBatchId);

    @Query("SELECT r FROM SalesReservation r " +
           "WHERE r.salesOrderItemId IN " +
           "  (SELECT i.id FROM SalesOrderItem i WHERE i.salesOrderId = :soId) " +
           "AND r.status = 'ACTIVE'")
    List<SalesReservation> findActiveByOrderId(@Param("soId") Long salesOrderId);

    @Query("SELECT COALESCE(SUM(r.reservedQty), 0) FROM SalesReservation r " +
           "WHERE r.fgBatchId = :batchId AND r.status = 'ACTIVE' AND r.salesOrderItemId = :soItemId")
    java.math.BigDecimal sumReservedQtyForBatchAndItem(
            @Param("batchId") Long fgBatchId, @Param("soItemId") Long salesOrderItemId);

    Optional<SalesReservation> findBySalesOrderItemIdAndFgBatchIdAndStatus(
            Long salesOrderItemId, Long fgBatchId, String status);

    @Query("SELECT r FROM SalesReservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<SalesReservation> findExpired(@Param("now") Instant now);
}

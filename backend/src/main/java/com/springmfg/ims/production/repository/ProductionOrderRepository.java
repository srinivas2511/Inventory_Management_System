package com.springmfg.ims.production.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.production.domain.ProductionOrder;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    List<ProductionOrder> findByStatus(String status);

    List<ProductionOrder> findByProductId(Long productId);

    List<ProductionOrder> findBySupervisorId(Long supervisorId);

    @Query("SELECT po FROM ProductionOrder po WHERE " +
           "(:status IS NULL OR po.status = :status) AND (:productId IS NULL OR po.productId = :productId)")
    Page<ProductionOrder> search(@Param("status") String status,
                                 @Param("productId") Long productId,
                                 Pageable pageable);

    @Query("SELECT COUNT(po) > 0 FROM ProductionOrder po " +
           "LEFT JOIN ProductionOperation op ON op.productionOrderId = po.id " +
           "WHERE po.id = :orderId AND (po.supervisorId = :userId OR op.assignedOperatorId = :userId)")
    boolean isAccessibleByUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Query("SELECT po FROM ProductionOrder po WHERE " +
           "(:status IS NULL OR po.status = :status) AND (:productId IS NULL OR po.productId = :productId) AND " +
           "(:scoped = FALSE OR po.plantId IN :plantIds)")
    Page<ProductionOrder> searchInPlants(@Param("status") String status,
                                         @Param("productId") Long productId,
                                         @Param("scoped") boolean scoped,
                                         @Param("plantIds") java.util.Collection<Long> plantIds,
                                         Pageable pageable);

    @Query("SELECT po FROM ProductionOrder po WHERE po.status = 'IN_PROGRESS' AND po.expectedCompletionDate < :today")
    List<ProductionOrder> findDelayed(@Param("today") LocalDate today);

    @Query("SELECT COUNT(po) FROM ProductionOrder po WHERE po.status IN ('APPROVED','RELEASED','IN_PROGRESS')")
    long countInProgress();

    @Query("SELECT COALESCE(SUM(po.plannedQty), 0) FROM ProductionOrder po WHERE po.status NOT IN ('DRAFT','CANCELLED') AND MONTH(po.createdAt) = MONTH(CURRENT_DATE) AND YEAR(po.createdAt) = YEAR(CURRENT_DATE)")
    java.math.BigDecimal sumPlannedQtyThisMonth();

    @Query("SELECT COALESCE(SUM(po.goodQty), 0) FROM ProductionOrder po WHERE po.status NOT IN ('DRAFT','CANCELLED') AND MONTH(po.createdAt) = MONTH(CURRENT_DATE) AND YEAR(po.createdAt) = YEAR(CURRENT_DATE)")
    java.math.BigDecimal sumGoodQtyThisMonth();
}

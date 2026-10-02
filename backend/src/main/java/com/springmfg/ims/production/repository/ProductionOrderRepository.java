package com.springmfg.ims.production.repository;

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
}

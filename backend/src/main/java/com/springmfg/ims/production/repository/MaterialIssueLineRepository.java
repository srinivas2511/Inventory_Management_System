package com.springmfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.production.domain.MaterialIssueLine;

public interface MaterialIssueLineRepository extends JpaRepository<MaterialIssueLine, Long> {

    @Query("SELECT l FROM MaterialIssueLine l WHERE l.materialIssue.id = :issueId")
    List<MaterialIssueLine> findByMaterialIssueId(@Param("issueId") Long issueId);

    /** Every line the order has issued, oldest first (the order in which batches are consumed). */
    @Query("SELECT l FROM MaterialIssueLine l WHERE l.materialIssue.productionOrderId = :orderId ORDER BY l.id")
    List<MaterialIssueLine> findByProductionOrder(@Param("orderId") Long orderId);

    @Query("SELECT l FROM MaterialIssueLine l WHERE l.materialIssue.productionOrderId = :orderId "
            + "AND l.materialId = :materialId ORDER BY l.id")
    List<MaterialIssueLine> findByProductionOrderAndMaterial(@Param("orderId") Long orderId,
                                                             @Param("materialId") Long materialId);
}

package com.springmfg.ims.production.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.production.domain.ProductionOperation;

public interface ProductionOperationRepository extends JpaRepository<ProductionOperation, Long> {

    List<ProductionOperation> findByProductionOrderIdOrderBySequenceNo(Long productionOrderId);

    List<ProductionOperation> findByProductionOrderId(Long productionOrderId);

    List<ProductionOperation> findByProductionOrderIdAndAssignedOperatorId(Long productionOrderId, Long operatorId);

    @Query("SELECT op FROM ProductionOperation op WHERE op.assignedOperatorId = :operatorId " +
           "AND op.status IN ('PENDING','IN_PROGRESS','PAUSED')")
    List<ProductionOperation> findMyWork(@Param("operatorId") Long operatorId);

    boolean existsByProductionOrderIdAndSequenceNoGreaterThan(Long productionOrderId, int sequenceNo);

    @Query("SELECT COALESCE(MAX(o.entryNo), 0) FROM ProductionOutput o WHERE o.productionOperationId = :opId")
    int findMaxEntryNo(@Param("opId") Long opId);

    Optional<ProductionOperation> findByProductionOrderIdAndSequenceNo(Long productionOrderId, int sequenceNo);
}

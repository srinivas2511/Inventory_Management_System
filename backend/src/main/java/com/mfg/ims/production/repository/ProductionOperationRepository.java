package com.mfg.ims.production.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.production.domain.ProductionOperation;

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

    Optional<ProductionOperation> findFirstByProductionOrderIdAndSequenceNoLessThanOrderBySequenceNoDesc(
            Long productionOrderId, int sequenceNo);

    /**
     * Why a PENDING operation may not start yet, or null when it may: the routing is worked in sequence, so the
     * operation before it must be COMPLETED first (DESIGN 4.7, UAT PRD-05).
     */
    default String startBlockedReason(ProductionOperation op) {
        if (!"PENDING".equals(op.getStatus())) return null;
        return findFirstByProductionOrderIdAndSequenceNoLessThanOrderBySequenceNoDesc(
                        op.getProductionOrderId(), op.getSequenceNo())
                .filter(previous -> !"COMPLETED".equals(previous.getStatus()))
                .map(previous -> "Operation " + previous.getSequenceNo() + " must be completed first (it is "
                        + previous.getStatus() + ")")
                .orElse(null);
    }
}

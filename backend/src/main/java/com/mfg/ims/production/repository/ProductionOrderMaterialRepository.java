package com.mfg.ims.production.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.mfg.ims.production.domain.ProductionOrderMaterial;

public interface ProductionOrderMaterialRepository extends JpaRepository<ProductionOrderMaterial, Long> {

    List<ProductionOrderMaterial> findByProductionOrderId(Long productionOrderId);

    /** Serialises consumption, returns and write-offs of one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ProductionOrderMaterial m WHERE m.productionOrderId = :orderId ORDER BY m.id")
    List<ProductionOrderMaterial> lockByProductionOrderId(@Param("orderId") Long orderId);

    Optional<ProductionOrderMaterial> findByProductionOrderIdAndMaterialId(Long productionOrderId, Long materialId);

    boolean existsByProductionOrderId(Long productionOrderId);
}

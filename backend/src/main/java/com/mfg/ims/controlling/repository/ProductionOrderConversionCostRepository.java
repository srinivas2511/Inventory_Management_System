package com.mfg.ims.controlling.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.controlling.domain.ProductionOrderConversionCost;

import jakarta.persistence.LockModeType;

public interface ProductionOrderConversionCostRepository extends JpaRepository<ProductionOrderConversionCost, Long> {

    Optional<ProductionOrderConversionCost> findByProductionOrderId(Long productionOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ProductionOrderConversionCost c WHERE c.productionOrderId = :orderId")
    Optional<ProductionOrderConversionCost> lockByProductionOrderId(@Param("orderId") Long orderId);
}

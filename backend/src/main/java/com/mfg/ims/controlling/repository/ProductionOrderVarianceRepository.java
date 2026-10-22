package com.mfg.ims.controlling.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.controlling.domain.ProductionOrderVariance;

public interface ProductionOrderVarianceRepository extends JpaRepository<ProductionOrderVariance, Long> {

    Optional<ProductionOrderVariance> findByProductionOrderId(Long productionOrderId);
}

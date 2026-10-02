package com.springmfg.ims.controlling.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.controlling.domain.ProductionOrderVariance;

public interface ProductionOrderVarianceRepository extends JpaRepository<ProductionOrderVariance, Long> {

    Optional<ProductionOrderVariance> findByProductionOrderId(Long productionOrderId);
}

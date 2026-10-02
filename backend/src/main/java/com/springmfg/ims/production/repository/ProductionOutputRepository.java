package com.springmfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.production.domain.ProductionOutput;

public interface ProductionOutputRepository extends JpaRepository<ProductionOutput, Long> {

    List<ProductionOutput> findByProductionOperationId(Long operationId);

    List<ProductionOutput> findByProductionOrderId(Long orderId);
}

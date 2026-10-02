package com.springmfg.ims.production.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.production.domain.ProductionOrderMaterial;

public interface ProductionOrderMaterialRepository extends JpaRepository<ProductionOrderMaterial, Long> {

    List<ProductionOrderMaterial> findByProductionOrderId(Long productionOrderId);

    Optional<ProductionOrderMaterial> findByProductionOrderIdAndMaterialId(Long productionOrderId, Long materialId);
}

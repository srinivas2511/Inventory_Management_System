package com.springmfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.production.domain.ProductionDowntime;

public interface ProductionDowntimeRepository extends JpaRepository<ProductionDowntime, Long> {

    List<ProductionDowntime> findByMachineId(Long machineId);

    List<ProductionDowntime> findByProductionOrderId(Long productionOrderId);
}

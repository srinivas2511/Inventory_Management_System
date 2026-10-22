package com.mfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.production.domain.ProductionDowntime;

public interface ProductionDowntimeRepository extends JpaRepository<ProductionDowntime, Long> {

    List<ProductionDowntime> findByMachineId(Long machineId);

    List<ProductionDowntime> findByProductionOrderId(Long productionOrderId);
}

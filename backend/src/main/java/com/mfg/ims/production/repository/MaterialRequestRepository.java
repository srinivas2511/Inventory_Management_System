package com.mfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.production.domain.MaterialRequest;

public interface MaterialRequestRepository extends JpaRepository<MaterialRequest, Long> {

    List<MaterialRequest> findByProductionOrderIdAndStatus(Long productionOrderId, String status);
}

package com.mfg.ims.procurement.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.procurement.domain.PurchaseRequisition;

public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    Optional<PurchaseRequisition> findByPrNumber(String prNumber);

    Page<PurchaseRequisition> findByStatus(String status, Pageable pageable);

    Page<PurchaseRequisition> findByRequestedBy(Long userId, Pageable pageable);
}

package com.mfg.ims.sales.challan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallanRepository extends JpaRepository<DeliveryChallan, Long> {
    List<DeliveryChallan> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

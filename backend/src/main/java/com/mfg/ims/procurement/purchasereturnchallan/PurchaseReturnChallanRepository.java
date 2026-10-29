package com.mfg.ims.procurement.purchasereturnchallan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReturnChallanRepository extends JpaRepository<PurchaseReturnChallan, Long> {
    List<PurchaseReturnChallan> findByCompanyIdOrderByDocDateDescIdDesc(Long companyId);
}

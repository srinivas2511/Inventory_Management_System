package com.mfg.ims.procurement.purchasereturn;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {
    List<PurchaseReturn> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

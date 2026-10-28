package com.mfg.ims.procurement.purchaseinvoice;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    List<PurchaseInvoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

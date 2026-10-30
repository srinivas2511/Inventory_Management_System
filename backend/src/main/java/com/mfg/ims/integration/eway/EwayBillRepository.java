package com.mfg.ims.integration.eway;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EwayBillRepository extends JpaRepository<EwayBill, Long> {
    List<EwayBill> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<EwayBill> findByCompanyIdAndInvoiceIdOrderByCreatedAtDesc(Long companyId, Long invoiceId);
}

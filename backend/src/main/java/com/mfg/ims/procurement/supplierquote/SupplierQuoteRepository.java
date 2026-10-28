package com.mfg.ims.procurement.supplierquote;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierQuoteRepository extends JpaRepository<SupplierQuote, Long> {
    List<SupplierQuote> findByRfqIdOrderByCreatedAtDesc(Long rfqId);
    List<SupplierQuote> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

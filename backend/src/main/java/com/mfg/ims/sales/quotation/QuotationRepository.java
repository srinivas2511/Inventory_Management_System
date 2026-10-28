package com.mfg.ims.sales.quotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

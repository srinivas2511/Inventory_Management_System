package com.mfg.ims.sales.quotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRevisionRepository extends JpaRepository<QuotationRevision, Long> {
    List<QuotationRevision> findByQuotationIdOrderByRevisionNumberDesc(Long quotationId);
}

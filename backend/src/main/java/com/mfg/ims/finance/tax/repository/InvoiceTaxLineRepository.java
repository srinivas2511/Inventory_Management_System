package com.mfg.ims.finance.tax.repository;

import com.mfg.ims.finance.tax.domain.InvoiceTaxLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceTaxLineRepository extends JpaRepository<InvoiceTaxLine, Long> {

    List<InvoiceTaxLine> findByDocumentTypeAndDocumentIdOrderByIdAsc(String documentType, Long documentId);
}

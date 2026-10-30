package com.mfg.ims.sales.quotation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    List<Quotation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /** Count linked order confirmations that reference the given quotation (for cancel cascade check). */
    @Query("SELECT COUNT(oc) FROM OrderConfirmation oc WHERE oc.quotationId = :quotationId AND oc.status <> 'CANCELLED'")
    long countLinkedOrderConfirmations(@Param("quotationId") Long quotationId);
}

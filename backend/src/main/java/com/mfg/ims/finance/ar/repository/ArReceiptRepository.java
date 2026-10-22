package com.mfg.ims.finance.ar.repository;

import com.mfg.ims.finance.ar.domain.ArReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ArReceiptRepository extends JpaRepository<ArReceipt, Long> {
    List<ArReceipt> findByArInvoiceId(Long arInvoiceId);

    /** The INR already taken off the receivable by earlier receipts of an invoice. */
    @Query("SELECT COALESCE(SUM(r.carryingInr), 0) FROM ArReceipt r WHERE r.arInvoiceId = :invoiceId")
    BigDecimal sumCarryingInr(@Param("invoiceId") Long invoiceId);
}

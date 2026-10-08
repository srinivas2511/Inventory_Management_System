package com.springmfg.ims.finance.ap.repository;

import com.springmfg.ims.finance.ap.domain.ApPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ApPaymentRepository extends JpaRepository<ApPayment, Long> {
    List<ApPayment> findByApInvoiceId(Long apInvoiceId);

    /** The INR already taken off the payable by earlier payments of an invoice. */
    @Query("SELECT COALESCE(SUM(p.carryingInr), 0) FROM ApPayment p WHERE p.apInvoiceId = :invoiceId")
    BigDecimal sumCarryingInr(@Param("invoiceId") Long invoiceId);
}

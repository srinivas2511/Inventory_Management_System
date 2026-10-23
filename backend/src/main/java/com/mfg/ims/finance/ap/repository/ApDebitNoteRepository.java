package com.mfg.ims.finance.ap.repository;

import com.mfg.ims.finance.ap.domain.ApDebitNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ApDebitNoteRepository extends JpaRepository<ApDebitNote, Long> {

    /** An invoice id of 0 lists every debit note; the sentinel avoids an untyped null parameter on PostgreSQL. */
    @Query("SELECT d FROM ApDebitNote d WHERE d.apInvoiceId = :invoiceId OR :invoiceId = 0L")
    Page<ApDebitNote> search(@Param("invoiceId") long invoiceId, Pageable pageable);

    /** Non-cancelled debit notes commit their amounts against the invoice's open balance. */
    @Query("SELECT COALESCE(SUM(d.subtotal),0) FROM ApDebitNote d WHERE d.apInvoiceId=:id AND d.status<>'CANCELLED'")
    BigDecimal sumSubtotal(@Param("id") Long invoiceId);

    @Query("SELECT COALESCE(SUM(d.taxTotal),0) FROM ApDebitNote d WHERE d.apInvoiceId=:id AND d.status<>'CANCELLED'")
    BigDecimal sumTax(@Param("id") Long invoiceId);

    @Query("SELECT COALESCE(SUM(d.totalAmount),0) FROM ApDebitNote d WHERE d.apInvoiceId=:id AND d.status<>'CANCELLED'")
    BigDecimal sumCommitted(@Param("id") Long invoiceId);

    @Query("SELECT COALESCE(SUM(d.carryingInr),0) FROM ApDebitNote d WHERE d.apInvoiceId=:id AND d.status='POSTED'")
    BigDecimal sumPostedCarryingInr(@Param("id") Long invoiceId);
}

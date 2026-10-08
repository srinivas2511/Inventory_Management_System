package com.springmfg.ims.finance.ar.repository;

import com.springmfg.ims.finance.ar.domain.CreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    List<CreditNote> findByArInvoiceIdOrderByIdAsc(Long arInvoiceId);

    /** An invoice id of 0 lists every credit note; the sentinel avoids an untyped null parameter on PostgreSQL. */
    @Query("SELECT c FROM CreditNote c WHERE c.arInvoiceId = :invoiceId OR :invoiceId = 0L")
    Page<CreditNote> search(@Param("invoiceId") long invoiceId, Pageable pageable);

    /** Amounts of the notes of an invoice that still count: a draft reserves its amount, a posted one has used it. */
    @Query("SELECT COALESCE(SUM(c.subtotal), 0) FROM CreditNote c WHERE c.arInvoiceId = :invoiceId AND c.status <> 'CANCELLED'")
    BigDecimal sumSubtotal(@Param("invoiceId") Long invoiceId);

    @Query("SELECT COALESCE(SUM(c.taxTotal), 0) FROM CreditNote c WHERE c.arInvoiceId = :invoiceId AND c.status <> 'CANCELLED'")
    BigDecimal sumTax(@Param("invoiceId") Long invoiceId);

    @Query("SELECT COALESCE(SUM(c.totalAmount), 0) FROM CreditNote c WHERE c.arInvoiceId = :invoiceId AND c.status <> 'CANCELLED'")
    BigDecimal sumCommitted(@Param("invoiceId") Long invoiceId);

    @Query("SELECT COALESCE(SUM(c.carryingInr), 0) FROM CreditNote c WHERE c.arInvoiceId = :invoiceId AND c.status = 'POSTED'")
    BigDecimal sumPostedCarryingInr(@Param("invoiceId") Long invoiceId);

    @Query("SELECT COALESCE(SUM(c.returnQuantity), 0) FROM CreditNote c WHERE c.arInvoiceId = :invoiceId "
            + "AND c.returnFgBatchId = :batchId AND c.status <> 'CANCELLED'")
    BigDecimal sumReturned(@Param("invoiceId") Long invoiceId, @Param("batchId") Long batchId);
}

package com.mfg.ims.finance.ar.repository;

import com.mfg.ims.finance.ar.domain.ArInvoice;
import com.mfg.ims.sales.domain.Dispatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArInvoiceRepository extends JpaRepository<ArInvoice, Long> {
    Optional<ArInvoice> findByDispatchId(Long dispatchId);
    Page<ArInvoice> findByCustomerId(Long customerId, Pageable pageable);
    List<ArInvoice> findByCustomerIdAndStatusIn(Long customerId, List<String> statuses);
    List<ArInvoice> findByStatusIn(List<String> statuses);

    @Query("SELECT d FROM Dispatch d WHERE d.status = 'DISPATCHED' "
            + "AND NOT EXISTS (SELECT 1 FROM ArInvoice a WHERE a.dispatchId = d.id) ORDER BY d.id")
    List<Dispatch> findDispatchesWithoutInvoice(Pageable pageable);
}

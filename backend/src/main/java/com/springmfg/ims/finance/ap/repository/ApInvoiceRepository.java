package com.springmfg.ims.finance.ap.repository;

import com.springmfg.ims.finance.ap.domain.ApInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApInvoiceRepository extends JpaRepository<ApInvoice, Long> {
    Page<ApInvoice> findBySupplierId(Long supplierId, Pageable pageable);
    List<ApInvoice> findBySupplierIdAndStatusIn(Long supplierId, List<String> statuses);
    List<ApInvoice> findByStatusIn(List<String> statuses);
}

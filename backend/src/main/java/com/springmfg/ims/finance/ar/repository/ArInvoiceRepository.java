package com.springmfg.ims.finance.ar.repository;

import com.springmfg.ims.finance.ar.domain.ArInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArInvoiceRepository extends JpaRepository<ArInvoice, Long> {
    Optional<ArInvoice> findByDispatchId(Long dispatchId);
    Page<ArInvoice> findByCustomerId(Long customerId, Pageable pageable);
    List<ArInvoice> findByCustomerIdAndStatusIn(Long customerId, List<String> statuses);
    List<ArInvoice> findByStatusIn(List<String> statuses);
}

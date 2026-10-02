package com.springmfg.ims.finance.ar.repository;

import com.springmfg.ims.finance.ar.domain.ArReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArReceiptRepository extends JpaRepository<ArReceipt, Long> {
    List<ArReceipt> findByArInvoiceId(Long arInvoiceId);
}

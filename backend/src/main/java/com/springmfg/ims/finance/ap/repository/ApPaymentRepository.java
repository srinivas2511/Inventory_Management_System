package com.springmfg.ims.finance.ap.repository;

import com.springmfg.ims.finance.ap.domain.ApPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApPaymentRepository extends JpaRepository<ApPayment, Long> {
    List<ApPayment> findByApInvoiceId(Long apInvoiceId);
}

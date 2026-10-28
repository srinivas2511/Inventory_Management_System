package com.mfg.ims.finance.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {
    List<SalesInvoice> findByCompanyIdOrderByDocDateDesc(Long companyId);
}

package com.mfg.ims.finance.advance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdvanceReceiptRepository extends JpaRepository<AdvanceReceipt, Long> {
    List<AdvanceReceipt> findByCompanyIdAndCustomerIdOrderByReceiptDateDesc(Long companyId, Long customerId);
    List<AdvanceReceipt> findByCompanyIdOrderByReceiptDateDesc(Long companyId);
}

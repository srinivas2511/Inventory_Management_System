package com.mfg.ims.finance.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByCompanyIdOrderByDocDateDesc(Long companyId);
}

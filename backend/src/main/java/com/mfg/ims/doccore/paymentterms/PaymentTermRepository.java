package com.mfg.ims.doccore.paymentterms;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTermRepository extends JpaRepository<PaymentTerm, Long> {
    List<PaymentTerm> findByCompanyIdAndActiveTrueOrderByTermCode(Long companyId);
    boolean existsByCompanyIdAndTermCode(Long companyId, String termCode);
}

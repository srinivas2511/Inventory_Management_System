package com.mfg.ims.procurement.rfq;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RfqRepository extends JpaRepository<Rfq, Long> {
    List<Rfq> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    boolean existsByCompanyIdAndDocNo(Long companyId, String docNo);
}

package com.mfg.ims.crm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mfg.ims.crm.domain.CrmQuotation;

public interface CrmQuotationRepository extends JpaRepository<CrmQuotation, Long>, JpaSpecificationExecutor<CrmQuotation> {

    List<CrmQuotation> findByStatusInAndValidUntilBefore(List<String> statuses, LocalDate date);
}

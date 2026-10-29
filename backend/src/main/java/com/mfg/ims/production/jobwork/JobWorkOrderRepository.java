package com.mfg.ims.production.jobwork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobWorkOrderRepository extends JpaRepository<JobWorkOrder, Long> {
    List<JobWorkOrder> findByCompanyIdOrderByIdDesc(Long companyId);
    Optional<JobWorkOrder> findByIdAndCompanyId(Long id, Long companyId);
}

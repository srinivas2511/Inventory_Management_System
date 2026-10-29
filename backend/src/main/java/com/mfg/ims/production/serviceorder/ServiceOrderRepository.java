package com.mfg.ims.production.serviceorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {
    List<ServiceOrder> findByCompanyIdOrderByIdDesc(Long companyId);
    Optional<ServiceOrder> findByIdAndCompanyId(Long id, Long companyId);
}

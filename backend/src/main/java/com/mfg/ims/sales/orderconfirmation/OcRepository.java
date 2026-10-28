package com.mfg.ims.sales.orderconfirmation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OcRepository extends JpaRepository<OrderConfirmation, Long> {
    List<OrderConfirmation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

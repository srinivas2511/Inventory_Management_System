package com.mfg.ims.production.serviceorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceConfirmationRepository extends JpaRepository<ServiceConfirmation, Long> {
    List<ServiceConfirmation> findBySoIdOrderByConfirmedAtDesc(Long soId);
}

package com.springmfg.ims.quality.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.quality.domain.RejectionReason;

public interface RejectionReasonRepository extends JpaRepository<RejectionReason, Long> {

    List<RejectionReason> findByActiveTrue();

    boolean existsByReasonCode(String reasonCode);
}

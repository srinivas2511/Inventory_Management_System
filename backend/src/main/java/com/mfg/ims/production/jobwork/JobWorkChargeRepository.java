package com.mfg.ims.production.jobwork;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobWorkChargeRepository extends JpaRepository<JobWorkCharge, Long> {
    List<JobWorkCharge> findByJwoIdOrderByIdAsc(Long jwoId);
}

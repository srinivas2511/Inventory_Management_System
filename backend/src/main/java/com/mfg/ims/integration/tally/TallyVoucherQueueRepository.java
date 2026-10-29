package com.mfg.ims.integration.tally;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TallyVoucherQueueRepository extends JpaRepository<TallyVoucherQueue, Long> {
    List<TallyVoucherQueue> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<TallyVoucherQueue> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);
}

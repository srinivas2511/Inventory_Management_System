package com.mfg.ims.integration.tally;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;

public interface TallyVoucherQueueRepository extends JpaRepository<TallyVoucherQueue, Long> {
    List<TallyVoucherQueue> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<TallyVoucherQueue> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);
    List<TallyVoucherQueue> findByCompanyIdAndStatusAndSentAtBetweenOrderBySentAtDesc(
            Long companyId, String status, OffsetDateTime from, OffsetDateTime to);
    long countByCompanyIdAndStatus(Long companyId, String status);
    long countByCompanyIdAndStatusAndReconciledAtIsNotNull(Long companyId, String status);
}

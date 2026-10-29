package com.mfg.ims.integration.calllog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IntegrationCallRepository extends JpaRepository<IntegrationCall, Long> {
    List<IntegrationCall> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<IntegrationCall> findByCompanyIdAndConnectorOrderByCreatedAtDesc(Long companyId, String connector);
}

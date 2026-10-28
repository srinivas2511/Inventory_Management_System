package com.mfg.ims.production.costrollup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CostRollupLogRepository extends JpaRepository<CostRollupLog, Long> {
    List<CostRollupLog> findByCompanyIdOrderByRolledAtDesc(Long companyId);
}

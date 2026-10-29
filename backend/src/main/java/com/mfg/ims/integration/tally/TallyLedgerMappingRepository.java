package com.mfg.ims.integration.tally;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TallyLedgerMappingRepository extends JpaRepository<TallyLedgerMapping, Long> {
    List<TallyLedgerMapping> findByCompanyIdOrderByGlAccountCode(Long companyId);
    Optional<TallyLedgerMapping> findByCompanyIdAndGlAccountCode(Long companyId, String glAccountCode);
}

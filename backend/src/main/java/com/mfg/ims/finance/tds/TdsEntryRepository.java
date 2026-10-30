package com.mfg.ims.finance.tds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TdsEntryRepository extends JpaRepository<TdsEntry, Long> {

    @Query("""
            SELECT e FROM TdsEntry e
            WHERE e.companyId = :companyId
              AND e.partyId = :partyId
            ORDER BY e.deductionDate DESC
            """)
    List<TdsEntry> findByParty(@Param("companyId") Long companyId,
                               @Param("partyId") Long partyId);

    List<TdsEntry> findByCompanyIdOrderByDeductionDateDesc(Long companyId);
}

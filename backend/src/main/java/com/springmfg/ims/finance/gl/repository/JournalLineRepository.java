package com.springmfg.ims.finance.gl.repository;

import com.springmfg.ims.finance.gl.domain.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByJournalHeaderId(Long journalHeaderId);

    @Query("SELECT l.glAccountId, " +
           "SUM(l.debitAmount) as totalDebit, " +
           "SUM(l.creditAmount) as totalCredit " +
           "FROM JournalLine l " +
           "JOIN l.journalHeader h " +
           "WHERE h.status = 'POSTED' AND h.periodId = :periodId " +
           "GROUP BY l.glAccountId")
    List<Object[]> trialBalanceByPeriod(@Param("periodId") Long periodId);
}

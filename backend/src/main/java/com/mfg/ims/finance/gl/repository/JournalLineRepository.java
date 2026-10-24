package com.mfg.ims.finance.gl.repository;

import com.mfg.ims.finance.gl.domain.JournalLine;
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
           "WHERE h.status IN ('POSTED', 'REVERSED') AND h.periodId = :periodId " +
           "GROUP BY l.glAccountId")
    List<Object[]> trialBalanceByPeriod(@Param("periodId") Long periodId);

    // R9-5.1: P&L — current fiscal year YTD up to (and including) the selected period
    @Query(value = """
            SELECT l.gl_account_id, SUM(l.debit_amount), SUM(l.credit_amount)
            FROM ims.journal_lines l
            JOIN ims.journal_headers h ON h.id = l.journal_header_id
            JOIN ims.fiscal_periods fp ON fp.id = h.period_id
            WHERE h.status IN ('POSTED','REVERSED')
              AND fp.fiscal_year  = (SELECT fiscal_year  FROM ims.fiscal_periods WHERE id = :periodId)
              AND fp.period_number <= (SELECT period_number FROM ims.fiscal_periods WHERE id = :periodId)
            GROUP BY l.gl_account_id
            """, nativeQuery = true)
    List<Object[]> plByFiscalYearUpToPeriod(@Param("periodId") Long periodId);

    // R9-5.1: Balance Sheet — cumulative from inception through end of selected period (all account types)
    @Query(value = """
            SELECT l.gl_account_id, SUM(l.debit_amount), SUM(l.credit_amount)
            FROM ims.journal_lines l
            JOIN ims.journal_headers h ON h.id = l.journal_header_id
            JOIN ims.fiscal_periods fp ON fp.id = h.period_id
            WHERE h.status IN ('POSTED','REVERSED')
              AND fp.start_date <= (SELECT end_date FROM ims.fiscal_periods WHERE id = :periodId)
            GROUP BY l.gl_account_id
            """, nativeQuery = true)
    List<Object[]> allAccountsCumulativeUpToPeriod(@Param("periodId") Long periodId);

    // R9-5.1: Cash Flow — movements on the main bank account (code 1201) for the selected period
    @Query(value = """
            SELECT h.journal_type, h.source_module, SUM(l.debit_amount), SUM(l.credit_amount)
            FROM ims.journal_lines l
            JOIN ims.journal_headers h ON h.id = l.journal_header_id
            JOIN ims.gl_accounts ga ON ga.id = l.gl_account_id
            WHERE h.status IN ('POSTED','REVERSED')
              AND h.period_id = :periodId
              AND ga.account_code = '1201'
            GROUP BY h.journal_type, h.source_module
            """, nativeQuery = true)
    List<Object[]> cashFlowByPeriod(@Param("periodId") Long periodId);
}

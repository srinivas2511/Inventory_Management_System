package com.mfg.ims.finance.config.repository;

import com.mfg.ims.finance.config.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByFiscalYear(Integer fiscalYear);
    Optional<Budget> findByGlAccountIdAndFiscalYearAndPeriodId(
            Long glAccountId, Integer fiscalYear, Long periodId);
}

package com.mfg.ims.controlling.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.controlling.domain.CostCentreBudget;

public interface CostCentreBudgetRepository extends JpaRepository<CostCentreBudget, Long> {

    Optional<CostCentreBudget> findByCostCentreIdAndGlAccountIdAndPeriodId(Long costCentreId, Long glAccountId, Long periodId);

    @Query(value = "SELECT b.* FROM ims.cost_centre_budgets b JOIN ims.fiscal_periods p ON p.id = b.period_id "
            + "WHERE b.cost_centre_id = :costCentreId AND p.fiscal_year = :fiscalYear ORDER BY p.period_number, b.gl_account_id",
            nativeQuery = true)
    List<CostCentreBudget> findByCostCentreAndFiscalYear(@Param("costCentreId") Long costCentreId,
                                                         @Param("fiscalYear") int fiscalYear);
}

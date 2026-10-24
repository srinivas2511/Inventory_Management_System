package com.mfg.ims.controlling.repository;

import com.mfg.ims.controlling.domain.CostAllocationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CostAllocationRuleRepository extends JpaRepository<CostAllocationRule, Long> {

    List<CostAllocationRule> findAllByOrderByCostCentreIdAscAllocationBaseAsc();

    List<CostAllocationRule> findByActiveTrue();

    @Query(value = """
            SELECT car.id, car.cost_centre_id, cc.cost_centre_code, cc.name,
                   car.allocation_base, car.active
            FROM ims.cost_allocation_rules car
            JOIN ims.cost_centres cc ON cc.id = car.cost_centre_id
            ORDER BY cc.cost_centre_code, car.allocation_base
            """, nativeQuery = true)
    List<Object[]> findAllWithCostCentre();

    /** Sum of production order labour/machine hours or output qty for a product in a period.
     *  base = LABOUR_HOURS: EPOCH seconds of operation time / 3600
     *  base = MACHINE_HOURS: same, but only ops with a machine assigned
     *  base = PRODUCTION_QTY: sum of good_qty from the last operation per production order
     */
    @Query(value = """
            SELECT po.product_id,
                   COALESCE(SUM(
                       CASE :base
                           WHEN 'LABOUR_HOURS' THEN
                               CASE WHEN pop.started_at IS NOT NULL AND pop.completed_at IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (pop.completed_at - pop.started_at)) / 3600.0
                                    ELSE 0 END
                           WHEN 'MACHINE_HOURS' THEN
                               CASE WHEN pop.machine_id IS NOT NULL
                                         AND pop.started_at IS NOT NULL AND pop.completed_at IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (pop.completed_at - pop.started_at)) / 3600.0
                                    ELSE 0 END
                           ELSE pop.good_qty
                       END
                   ), 0) AS base_total
            FROM ims.production_orders po
            JOIN ims.production_operations pop ON pop.production_order_id = po.id
            WHERE po.status IN ('COMPLETED','CLOSED')
              AND pop.completed_at >= (SELECT fp.start_date::TIMESTAMPTZ FROM ims.fiscal_periods fp WHERE fp.id = :periodId)
              AND pop.completed_at <  (SELECT (fp.end_date + INTERVAL '1 day')::TIMESTAMPTZ FROM ims.fiscal_periods fp WHERE fp.id = :periodId)
            GROUP BY po.product_id
            HAVING COALESCE(SUM(
                       CASE :base
                           WHEN 'LABOUR_HOURS' THEN
                               CASE WHEN pop.started_at IS NOT NULL AND pop.completed_at IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (pop.completed_at - pop.started_at)) / 3600.0
                                    ELSE 0 END
                           WHEN 'MACHINE_HOURS' THEN
                               CASE WHEN pop.machine_id IS NOT NULL
                                         AND pop.started_at IS NOT NULL AND pop.completed_at IS NOT NULL
                                    THEN EXTRACT(EPOCH FROM (pop.completed_at - pop.started_at)) / 3600.0
                                    ELSE 0 END
                           ELSE pop.good_qty
                       END
                   ), 0) > 0
            """, nativeQuery = true)
    List<Object[]> productBaseQty(@Param("periodId") Long periodId, @Param("base") String base);

    /** Overhead accumulated in a cost centre for the period (net expense debit amount). */
    @Query(value = """
            SELECT COALESCE(SUM(jl.debit_amount - jl.credit_amount), 0)
            FROM ims.journal_lines jl
            JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
            JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
            JOIN ims.fiscal_periods fp ON fp.id = jh.period_id
            WHERE jl.cost_centre_id = :costCentreId
              AND jh.status IN ('POSTED','REVERSED')
              AND fp.id = :periodId
              AND ga.account_type = 'EXPENSE'
            """, nativeQuery = true)
    java.math.BigDecimal costCentreOverhead(@Param("costCentreId") Long costCentreId,
                                             @Param("periodId") Long periodId);
}

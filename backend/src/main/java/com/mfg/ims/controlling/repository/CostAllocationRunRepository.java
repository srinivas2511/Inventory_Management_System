package com.mfg.ims.controlling.repository;

import com.mfg.ims.controlling.domain.CostAllocationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CostAllocationRunRepository extends JpaRepository<CostAllocationRun, Long> {

    List<CostAllocationRun> findByPeriodIdOrderByRunAtDesc(Long periodId);

    List<CostAllocationRun> findAllByOrderByRunAtDesc();

    @Query(value = """
            SELECT cal.id, cal.cost_centre_id, cc.cost_centre_code,
                   cal.product_id, p.product_code, p.name,
                   cal.allocation_base, cal.base_qty, cal.rate_per_unit, cal.allocated_amount
            FROM ims.cost_allocation_lines cal
            JOIN ims.cost_centres cc ON cc.id = cal.cost_centre_id
            JOIN ims.products p ON p.id = cal.product_id
            WHERE cal.run_id = :runId
            ORDER BY cc.cost_centre_code, p.product_code
            """, nativeQuery = true)
    List<Object[]> findLinesForRun(@Param("runId") Long runId);
}

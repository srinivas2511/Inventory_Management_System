package com.mfg.ims.maintenance.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.maintenance.domain.MaintenancePrediction;

public interface MaintenancePredictionRepository extends JpaRepository<MaintenancePrediction, Long> {

    List<MaintenancePrediction> findByAcknowledgedFalseOrderByPredictedFailureAtAsc();

    List<MaintenancePrediction> findAllByOrderByGeneratedAtDesc();

    Optional<MaintenancePrediction> findTopByMachineIdAndAcknowledgedFalseOrderByGeneratedAtDesc(Long machineId);

    /** MTBF analysis: returns machine_id, failure_count, mtbf_hours, last_failure_at for machines
     *  with at least 2 unplanned downtime events in the lookback period. */
    @Query(value = """
        SELECT
            d.machine_id,
            m.machine_code,
            m.name                                          AS machine_name,
            COUNT(d.id)                                     AS failure_count,
            CASE WHEN COUNT(d.id) > 1
                 THEN ROUND(EXTRACT(EPOCH FROM (MAX(d.started_at) - MIN(d.started_at))) / 3600.0
                            / (COUNT(d.id) - 1), 2)
                 ELSE NULL
            END                                             AS mtbf_hours,
            MAX(d.started_at)                               AS last_failure_at
        FROM ims.production_downtime d
        JOIN ims.machines m ON m.id = d.machine_id
        WHERE d.started_at >= :since
          AND (d.category IS NULL OR d.category = 'UNPLANNED')
        GROUP BY d.machine_id, m.machine_code, m.name
        HAVING COUNT(d.id) >= :minFailures
        ORDER BY COUNT(d.id) DESC
        """, nativeQuery = true)
    List<Object[]> findMachineFailureStats(
        @Param("since") Instant since,
        @Param("minFailures") int minFailures
    );
}

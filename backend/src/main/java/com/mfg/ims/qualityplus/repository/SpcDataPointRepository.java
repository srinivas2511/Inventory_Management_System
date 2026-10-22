package com.mfg.ims.qualityplus.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.qualityplus.domain.SpcDataPoint;

public interface SpcDataPointRepository extends JpaRepository<SpcDataPoint, Long> {

    List<SpcDataPoint> findByControlChartIdOrderBySubgroupNoAscMeasurementNoAsc(Long controlChartId);

    Optional<SpcDataPoint> findTopByControlChartIdOrderBySubgroupNoDescMeasurementNoDesc(Long controlChartId);

    boolean existsByControlChartIdAndInspectionId(Long controlChartId, Long inspectionId);

    long countByControlChartId(Long controlChartId);

    long countByControlChartIdAndOutOfControlTrue(Long controlChartId);

    @Query("SELECT p FROM SpcDataPoint p WHERE p.controlChartId = :chartId AND p.measuredAt >= :from " +
           "AND p.measuredAt <= :to ORDER BY p.subgroupNo, p.measurementNo")
    List<SpcDataPoint> findInPeriod(@Param("chartId") Long chartId, @Param("from") Instant from,
                                    @Param("to") Instant to);
}

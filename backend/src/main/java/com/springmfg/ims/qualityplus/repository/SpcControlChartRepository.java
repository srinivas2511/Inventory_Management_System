package com.springmfg.ims.qualityplus.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.qualityplus.domain.SpcControlChart;

import jakarta.persistence.LockModeType;

public interface SpcControlChartRepository extends JpaRepository<SpcControlChart, Long> {

    List<SpcControlChart> findAllByOrderByIdDesc();

    List<SpcControlChart> findByProductIdOrderByIdDesc(Long productId);

    List<SpcControlChart> findByProductIdAndActiveTrue(Long productId);

    Optional<SpcControlChart> findByProductIdAndParameterNameAndActiveTrue(Long productId, String parameterName);

    /** Serialises concurrent data entry on one chart so subgroup numbering never forks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SpcControlChart c WHERE c.id = :id")
    Optional<SpcControlChart> findByIdForUpdate(@Param("id") Long id);
}

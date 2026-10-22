package com.mfg.ims.mrp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mfg.ims.mrp.domain.DemandForecast;

import java.time.LocalDate;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long>, JpaSpecificationExecutor<DemandForecast> {

    boolean existsByProductIdAndTargetDate(Long productId, LocalDate targetDate);
}

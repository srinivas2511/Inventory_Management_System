package com.springmfg.ims.mrp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.springmfg.ims.mrp.domain.DemandForecast;

import java.time.LocalDate;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long>, JpaSpecificationExecutor<DemandForecast> {

    boolean existsByProductIdAndTargetDate(Long productId, LocalDate targetDate);
}

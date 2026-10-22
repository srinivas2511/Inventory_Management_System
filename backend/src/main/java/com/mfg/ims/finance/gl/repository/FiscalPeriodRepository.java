package com.mfg.ims.finance.gl.repository;

import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalPeriodRepository extends JpaRepository<FiscalPeriod, Long> {

    List<FiscalPeriod> findByFiscalYearOrderByPeriodNumberAsc(Integer fiscalYear);

    @Query("SELECT p FROM FiscalPeriod p WHERE p.status = 'OPEN' AND p.startDate <= :date AND p.endDate >= :date")
    Optional<FiscalPeriod> findOpenPeriodForDate(@Param("date") LocalDate date);

    List<FiscalPeriod> findAllByOrderByFiscalYearAscPeriodNumberAsc();
}

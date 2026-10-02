package com.springmfg.ims.finance.config.repository;

import com.springmfg.ims.finance.config.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT r FROM ExchangeRate r WHERE r.fromCurrency = :from AND r.toCurrency = :to " +
           "AND r.effectiveDate <= :date ORDER BY r.effectiveDate DESC LIMIT 1")
    Optional<ExchangeRate> findLatestRate(@Param("from") String fromCurrency,
                                          @Param("to") String toCurrency,
                                          @Param("date") LocalDate date);
}

package com.mfg.ims.finance.config.repository;

import com.mfg.ims.finance.config.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndPeriodId(
            String fromCurrency, String toCurrency, Long periodId);
}

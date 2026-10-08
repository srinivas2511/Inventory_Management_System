package com.springmfg.ims.finance.config.service;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.finance.config.domain.ExchangeRate;
import com.springmfg.ims.finance.config.dto.ExchangeRateDto;
import com.springmfg.ims.finance.config.dto.ExchangeRateRequest;
import com.springmfg.ims.finance.config.repository.ExchangeRateRepository;
import com.springmfg.ims.finance.gl.domain.FiscalPeriod;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exchange rates into the functional currency (INR). The rate method is the transaction-date rate (decision #14): the rate
 * of the fiscal period that contains the date, because rates are kept per period.
 */
@Service
@Transactional
public class ExchangeRateService {

    public static final String FUNCTIONAL_CURRENCY = "INR";

    private final ExchangeRateRepository rates;
    private final FiscalPeriodRepository periods;
    private final AuditService audit;

    public ExchangeRateService(ExchangeRateRepository rates, FiscalPeriodRepository periods, AuditService audit) {
        this.rates = rates;
        this.periods = periods;
        this.audit = audit;
    }

    /** 1 for INR; otherwise the rate of the open period that holds {@code date}. A missing rate stops the document. */
    @Transactional(readOnly = true)
    public BigDecimal rateFor(String currency, LocalDate date) {
        if (currency == null || FUNCTIONAL_CURRENCY.equals(currency)) {
            return BigDecimal.ONE;
        }
        FiscalPeriod period = periods.findOpenPeriodForDate(date)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.PERIOD_CLOSED, "No open period for " + date));
        return rates.findByFromCurrencyAndToCurrencyAndPeriodId(currency, FUNCTIONAL_CURRENCY, period.getId())
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "There is no " + currency + " to " + FUNCTIONAL_CURRENCY + " exchange rate for period "
                                + period.getPeriodName() + ". Add it under Finance > Exchange rates."));
    }

    /** An amount in the document currency converted to INR at 4 decimals, half-up. */
    public static BigDecimal toInr(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateDto> list(Long periodId) {
        Map<Long, FiscalPeriod> byId = periods.findAll().stream()
                .collect(Collectors.toMap(FiscalPeriod::getId, Function.identity()));
        return rates.findAll().stream()
                .filter(r -> periodId == null || periodId.equals(r.getPeriodId()))
                .sorted(java.util.Comparator.comparing(ExchangeRate::getPeriodId).thenComparing(ExchangeRate::getFromCurrency))
                .map(r -> toDto(r, byId.get(r.getPeriodId())))
                .toList();
    }

    public ExchangeRateDto save(ExchangeRateRequest req) {
        if (FUNCTIONAL_CURRENCY.equals(req.fromCurrency())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "INR is the functional currency and has no rate");
        }
        FiscalPeriod period = periods.findById(req.periodId())
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", req.periodId()));
        var existing = rates.findByFromCurrencyAndToCurrencyAndPeriodId(req.fromCurrency(), FUNCTIONAL_CURRENCY, period.getId());
        ExchangeRate rate = existing.orElseGet(ExchangeRate::new);
        BigDecimal before = existing.map(ExchangeRate::getRate).orElse(null);
        rate.setFromCurrency(req.fromCurrency());
        rate.setToCurrency(FUNCTIONAL_CURRENCY);
        rate.setPeriodId(period.getId());
        rate.setRate(req.rate().setScale(6, RoundingMode.HALF_UP));
        rate = rates.save(rate);
        audit.record("EXCHANGE_RATE_SET", "EXCHANGE_RATE", String.valueOf(rate.getId()),
                before == null ? null : before.toPlainString(), rate.getRate().toPlainString(),
                req.fromCurrency() + " " + period.getPeriodName());
        return toDto(rate, period);
    }

    private ExchangeRateDto toDto(ExchangeRate r, FiscalPeriod p) {
        return new ExchangeRateDto(r.getId(), r.getFromCurrency(), r.getToCurrency(), r.getPeriodId(),
                p == null ? null : p.getPeriodName(), r.getRate());
    }
}

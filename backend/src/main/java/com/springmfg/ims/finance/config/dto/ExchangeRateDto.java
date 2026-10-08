package com.springmfg.ims.finance.config.dto;

import java.math.BigDecimal;

public record ExchangeRateDto(Long id, String fromCurrency, String toCurrency, Long periodId, String periodName,
                              BigDecimal rate) {}

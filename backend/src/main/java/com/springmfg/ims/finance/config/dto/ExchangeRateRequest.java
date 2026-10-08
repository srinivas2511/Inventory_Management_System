package com.springmfg.ims.finance.config.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** The rate of one foreign currency into INR for one fiscal period: 1 unit of {@code fromCurrency} = {@code rate} INR. */
public record ExchangeRateRequest(
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "Use a three-letter currency code such as USD") String fromCurrency,
        @NotNull Long periodId,
        @NotNull @DecimalMin(value = "0.000001", message = "The rate must be greater than zero") BigDecimal rate) {}

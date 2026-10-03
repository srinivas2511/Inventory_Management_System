package com.springmfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** A null customerId is the standard price for all customers. */
public record SavePriceRequest(
        Long customerId,
        @NotNull Long productId,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        @Positive BigDecimal minQty,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        Boolean active) {}

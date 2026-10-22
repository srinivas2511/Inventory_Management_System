package com.mfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Customer may be omitted when an opportunity with a customer is given. Item prices default from the price list. */
public record SaveQuotationRequest(
        Long opportunityId,
        Long customerId,
        LocalDate validUntil,
        @Size(max = 60) String paymentTerms,
        @Size(max = 500) String notes,
        @NotEmpty @Valid List<Item> items,
        Long version) {

    public record Item(
            @NotNull Long productId,
            @NotNull @Positive BigDecimal quantity,
            @PositiveOrZero BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountPct) {}
}

package com.mfg.ims.production.serviceorder;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateServiceOrderRequest(
        LocalDate docDate,
        @NotBlank String serviceKind,
        Long vendorId,
        Long customerId,
        String description,
        LocalDate scheduledDate,
        BigDecimal serviceCharge,
        BigDecimal taxAmount,
        String remarks
) {}

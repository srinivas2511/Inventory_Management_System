package com.mfg.ims.production.serviceorder;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateServiceOrderRequest(
        LocalDate docDate,
        String serviceKind,
        Long vendorId,
        Long customerId,
        String description,
        LocalDate scheduledDate,
        BigDecimal serviceCharge,
        BigDecimal taxAmount,
        String remarks
) {}

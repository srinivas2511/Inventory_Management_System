package com.mfg.ims.production.serviceorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ServiceOrderDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        String serviceKind,
        Long vendorId,
        Long customerId,
        String description,
        LocalDate scheduledDate,
        LocalDate completedDate,
        BigDecimal serviceCharge,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        String remarks,
        Instant createdAt
) {}

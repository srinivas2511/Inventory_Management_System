package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobWorkOrderDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        Long vendorId,
        String challanNo,
        LocalDate issueDate,
        LocalDate expectedReturn,
        LocalDate actualReturn,
        BigDecimal jobCharges,
        BigDecimal scrapQty,
        String remarks,
        Instant createdAt,
        List<JobWorkOrderItemDto> items
) {}

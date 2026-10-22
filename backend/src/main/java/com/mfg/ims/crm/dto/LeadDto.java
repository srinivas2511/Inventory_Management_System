package com.mfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LeadDto(
        Long id,
        String leadNumber,
        String companyName,
        Long contactId,
        String contactName,
        String source,
        String status,
        Long ownerId,
        String ownerName,
        BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        Long convertedCustomerId,
        Instant convertedAt,
        String disqualifyReason,
        String notes,
        Long version,
        List<String> allowedActions) {}

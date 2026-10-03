package com.springmfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OpportunityDto(
        Long id,
        String opportunityNumber,
        Long leadId,
        Long customerId,
        String customerName,
        Long contactId,
        String contactName,
        String description,
        String stage,
        int probabilityPct,
        BigDecimal estimatedValue,
        BigDecimal weightedValue,
        LocalDate expectedCloseDate,
        Long ownerId,
        String ownerName,
        Long convertedQuotationId,
        Instant wonAt,
        String lostReason,
        Long version,
        List<String> allowedActions) {}

package com.springmfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveOpportunityRequest(
        Long leadId,
        Long customerId,
        Long contactId,
        @NotBlank @Size(max = 200) String description,
        @Min(0) @Max(100) Integer probabilityPct,
        @PositiveOrZero BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        Long ownerId,
        Long version) {}

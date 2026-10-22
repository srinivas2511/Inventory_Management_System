package com.mfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveLeadRequest(
        @NotBlank @Size(max = 150) String companyName,
        Long contactId,
        @Size(max = 30) String source,
        Long ownerId,
        @PositiveOrZero BigDecimal estimatedValue,
        LocalDate expectedCloseDate,
        String notes,
        Long version) {}

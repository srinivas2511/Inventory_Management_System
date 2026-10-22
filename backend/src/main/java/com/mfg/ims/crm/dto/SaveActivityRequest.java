package com.mfg.ims.crm.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** At least one of customerId, contactId, leadId or opportunityId is required; the customer is derived when omitted. */
public record SaveActivityRequest(
        @NotBlank String activityType,
        Long customerId,
        Long contactId,
        Long leadId,
        Long opportunityId,
        @NotBlank @Size(max = 200) String subject,
        String notes,
        @Size(max = 200) String outcome,
        Instant activityDate,
        @PositiveOrZero Integer durationMinutes,
        Long ownerId,
        Long version) {}

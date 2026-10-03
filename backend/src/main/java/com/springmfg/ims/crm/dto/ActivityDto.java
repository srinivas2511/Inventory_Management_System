package com.springmfg.ims.crm.dto;

import java.time.Instant;

public record ActivityDto(
        Long id,
        String activityType,
        Long customerId,
        String customerName,
        Long contactId,
        String contactName,
        Long leadId,
        Long opportunityId,
        String subject,
        String notes,
        String outcome,
        Instant activityDate,
        Integer durationMinutes,
        Long ownerId,
        String ownerName,
        Long version) {}

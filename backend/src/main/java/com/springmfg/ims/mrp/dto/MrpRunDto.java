package com.springmfg.ims.mrp.dto;

import java.time.Instant;

public record MrpRunDto(
        Long id,
        String runNumber,
        Instant initiatedAt,
        Instant completedAt,
        int planningHorizonDays,
        boolean includeSafetyStock,
        String status,
        Long initiatedBy,
        Integer totalProposals,
        Integer capacityOverloads,
        String notes,
        String errorMessage) {}

package com.springmfg.ims.quality.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RejectionDto(
        Long id,
        Long inspectionId,
        Long productionOutputId,
        Long rejectionReasonId,
        String reasonCode,
        String reasonName,
        String itemType,
        Long productId,
        Long batchId,
        BigDecimal quantity,
        String disposition,
        String remarks,
        Instant createdAt
) {}

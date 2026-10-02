package com.springmfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationDto(
        Long id,
        Long salesOrderItemId,
        Long fgBatchId,
        String batchNumber,
        BigDecimal reservedQty,
        String status,
        Instant expiresAt) {}

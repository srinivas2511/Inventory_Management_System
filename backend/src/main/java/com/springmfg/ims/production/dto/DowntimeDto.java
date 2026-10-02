package com.springmfg.ims.production.dto;

import java.time.Instant;

public record DowntimeDto(
        Long id,
        Long machineId,
        Long productionOrderId,
        String reason,
        String category,
        Instant startedAt,
        Instant endedAt,
        Long reportedBy) {}

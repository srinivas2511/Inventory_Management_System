package com.springmfg.ims.production.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordDowntimeRequest(
        @NotNull Long machineId,
        Long productionOrderId,
        @NotBlank String reason,
        String category,
        Instant startedAt) {}

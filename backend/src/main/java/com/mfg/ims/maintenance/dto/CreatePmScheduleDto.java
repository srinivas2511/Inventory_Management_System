package com.mfg.ims.maintenance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePmScheduleDto(
        @NotNull Long machineId,
        @NotBlank String taskName,
        @Min(1) int frequencyDays,
        Long assignedTo) {}

package com.mfg.ims.maintenance.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMaintenanceRequestDto(
        @NotNull Long machineId,
        @NotBlank String requestType,
        int priority,
        @NotBlank String description,
        Long assignedTo,
        LocalDate scheduledDate) {}

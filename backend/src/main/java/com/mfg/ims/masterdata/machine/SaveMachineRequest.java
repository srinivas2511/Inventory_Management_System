package com.mfg.ims.masterdata.machine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveMachineRequest(
        @NotBlank String code,
        @NotBlank String name,
        String machineType,
        String productionLine,
        String status,
        Long costCentreId,
        @Size(max = 300) String description,
        Long workCentreId) {}

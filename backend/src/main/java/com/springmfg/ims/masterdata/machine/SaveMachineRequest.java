package com.springmfg.ims.masterdata.machine;

import jakarta.validation.constraints.NotBlank;

public record SaveMachineRequest(
        @NotBlank String code,
        @NotBlank String name,
        String machineType,
        String productionLine,
        String status,
        Long costCentreId) {}

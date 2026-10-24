package com.mfg.ims.controlling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveAllocationRuleRequest(
        @NotNull Long costCentreId,
        @NotBlank String allocationBase
) {}

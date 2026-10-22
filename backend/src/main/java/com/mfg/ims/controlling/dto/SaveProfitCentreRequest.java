package com.mfg.ims.controlling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveProfitCentreRequest(
        @NotBlank @Size(max = 20) String profitCentreCode,
        @NotBlank @Size(max = 100) String name
) {}

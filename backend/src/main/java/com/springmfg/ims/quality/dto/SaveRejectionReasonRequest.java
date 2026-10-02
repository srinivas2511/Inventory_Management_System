package com.springmfg.ims.quality.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveRejectionReasonRequest(
        @NotBlank @Size(max = 20) String reasonCode,
        @NotBlank @Size(max = 100) String name,
        String appliesTo
) {}

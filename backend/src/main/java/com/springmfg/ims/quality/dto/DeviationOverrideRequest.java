package com.springmfg.ims.quality.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviationOverrideRequest(@NotBlank String deviationNote) {}

package com.mfg.ims.finance.ap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApMatchOverrideRequest(@NotBlank @Size(max = 500) String reason) {}

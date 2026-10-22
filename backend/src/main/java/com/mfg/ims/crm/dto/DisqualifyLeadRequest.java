package com.mfg.ims.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisqualifyLeadRequest(@NotBlank @Size(max = 300) String reason) {}

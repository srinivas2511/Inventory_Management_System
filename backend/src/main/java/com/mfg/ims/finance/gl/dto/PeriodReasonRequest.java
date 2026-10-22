package com.mfg.ims.finance.gl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason for finalizing a period close or for reopening a closed period; kept in the audit trail (DESIGN §15.6). */
public record PeriodReasonRequest(@NotBlank(message = "A reason is required") @Size(max = 300) String reason) {}

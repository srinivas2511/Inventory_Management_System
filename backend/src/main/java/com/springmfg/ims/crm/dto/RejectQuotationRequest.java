package com.springmfg.ims.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectQuotationRequest(@NotBlank @Size(max = 300) String reason) {}

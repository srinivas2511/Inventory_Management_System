package com.springmfg.ims.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdvanceOpportunityRequest(
        @NotBlank String stage,
        @Size(max = 200) String lostReason) {}

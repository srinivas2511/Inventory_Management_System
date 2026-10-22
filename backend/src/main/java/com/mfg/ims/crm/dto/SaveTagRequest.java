package com.mfg.ims.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveTagRequest(
        @NotBlank @Pattern(regexp = "INDUSTRY|REGION|TIER|STRATEGIC|OTHER",
                message = "tagGroup must be INDUSTRY, REGION, TIER, STRATEGIC or OTHER") String tagGroup,
        @NotBlank @Size(max = 60) String name,
        Boolean active) {}

package com.mfg.ims.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveLocationRequest(
        @NotBlank @Size(max = 20) String locationCode,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "RAW_MATERIAL|WIP|FINISHED|QUARANTINE|QUALITY_PENDING|SCRAP|DISPATCH") String locationType) {
}

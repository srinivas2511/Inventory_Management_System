package com.mfg.ims.masterdata.workcentre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveWorkCentreRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String description,
        boolean active,
        Long version) {}

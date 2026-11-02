package com.mfg.ims.procurement.vendortype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveVendorTypeRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 200) String description,
        boolean active,
        Long version) {}

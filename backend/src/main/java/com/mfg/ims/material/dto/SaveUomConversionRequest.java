package com.mfg.ims.material.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveUomConversionRequest(
        @NotBlank @Size(max = 10) String altUom,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal factorToBase) {
}

package com.springmfg.ims.sales.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCustomerSpecOverrideRequest(
        @NotBlank @Size(max = 60) String parameterName,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        BigDecimal nominal) {
}

package com.springmfg.ims.production.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Returns issued but unused material from an order's WIP to raw-material stock. */
public record ReturnMaterialRequest(
        @NotNull Long materialId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @Size(max = 500) String remarks) {
}

package com.springmfg.ims.material.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveMaterialRequest(
        @NotBlank @Size(max = 30) String materialCode,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 40) String materialType,
        @Size(max = 30) String grade,
        BigDecimal diameterMm,
        @NotBlank @Size(max = 10) String uom,
        Long preferredSupplierId,
        @NotNull @DecimalMin("0") BigDecimal minStock,
        @NotNull @DecimalMin("0") BigDecimal reorderLevel,
        BigDecimal maxStock,
        @NotNull @DecimalMin("0") BigDecimal standardCost,
        Integer shelfLifeDays,
        @Size(max = 500) String description,
        Long version) {
}

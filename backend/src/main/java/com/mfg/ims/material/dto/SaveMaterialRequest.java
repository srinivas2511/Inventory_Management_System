package com.mfg.ims.material.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveMaterialRequest(
        @Size(max = 30) String materialCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 40) String materialType,
        @Size(max = 30) String grade,
        BigDecimal diameterMm,
        @NotBlank @Size(max = 10) String uom,
        Long preferredSupplierId,
        @DecimalMin("0") BigDecimal minStock,
        @DecimalMin("0") BigDecimal reorderLevel,
        BigDecimal maxStock,
        @DecimalMin("0") BigDecimal standardCost,
        Integer shelfLifeDays,
        @Size(max = 500) String description,
        boolean serviceItem,
        @Pattern(regexp = "^(LOCAL|IMPORT)$", message = "sourceType must be LOCAL or IMPORT")
        String sourceType,
        boolean customerMaterial,
        Long version,
        @Size(max = 8) String hsnCode,
        BigDecimal minOrderQty) {
}

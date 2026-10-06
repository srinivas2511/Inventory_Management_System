package com.springmfg.ims.product.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update body for the attribute-definition admin screen. productType and attributeCode never change. */
public record SaveAttributeDefinitionRequest(
        @NotBlank @Size(max = 20) String productType,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,39}",
                message = "must be lower snake case, 2-40 characters, starting with a letter") String attributeCode,
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Pattern(regexp = "NUMBER|TEXT|ENUM|BOOLEAN") String dataType,
        @Size(max = 10) String unit,
        boolean required,
        BigDecimal minValue,
        BigDecimal maxValue,
        List<@NotBlank @Size(max = 60) String> enumValues,
        @Min(0) int displayOrder) {
}

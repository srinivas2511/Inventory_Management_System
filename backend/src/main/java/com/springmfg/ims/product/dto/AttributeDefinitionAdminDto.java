package com.springmfg.ims.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record AttributeDefinitionAdminDto(
        Long id,
        String productType,
        String attributeCode,
        String label,
        String dataType,
        String unit,
        boolean required,
        BigDecimal minValue,
        BigDecimal maxValue,
        List<String> enumValues,
        int displayOrder,
        boolean active) {
}

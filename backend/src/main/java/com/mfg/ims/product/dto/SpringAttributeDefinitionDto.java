package com.mfg.ims.product.dto;

import java.math.BigDecimal;

import com.mfg.ims.product.domain.SpringAttributeDefinition;

public record SpringAttributeDefinitionDto(
        Long id,
        String springType,
        String attributeCode,
        String label,
        String dataType,
        String unit,
        boolean required,
        BigDecimal minValue,
        BigDecimal maxValue,
        String enumValues,
        int displayOrder) {

    public static SpringAttributeDefinitionDto from(SpringAttributeDefinition d) {
        return new SpringAttributeDefinitionDto(
            d.getId(), d.getSpringType(), d.getAttributeCode(), d.getLabel(),
            d.getDataType(), d.getUnit(), d.isRequired(), d.getMinValue(), d.getMaxValue(),
            d.getEnumValues(), d.getDisplayOrder());
    }
}

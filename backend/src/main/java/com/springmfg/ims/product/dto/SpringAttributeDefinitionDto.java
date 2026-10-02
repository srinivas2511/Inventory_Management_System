package com.springmfg.ims.product.dto;

import com.springmfg.ims.product.domain.SpringAttributeDefinition;

public record SpringAttributeDefinitionDto(
        Long id,
        String springType,
        String attributeCode,
        String label,
        String dataType,
        String inputType,
        String options,
        boolean required,
        int sortOrder) {

    public static SpringAttributeDefinitionDto from(SpringAttributeDefinition d) {
        return new SpringAttributeDefinitionDto(
            d.getId(), d.getSpringType(), d.getAttributeCode(), d.getLabel(),
            d.getDataType(), d.getInputType(), d.getOptions(), d.isRequired(), d.getSortOrder());
    }
}

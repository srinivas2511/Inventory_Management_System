package com.mfg.ims.product.dto;

import com.mfg.ims.product.domain.ProductType;

public record ProductTypeDto(Long id, String code, String name, String industry, int displayOrder) {

    public static ProductTypeDto from(ProductType t) {
        return new ProductTypeDto(t.getId(), t.getCode(), t.getName(), t.getIndustry(), t.getDisplayOrder());
    }
}

package com.springmfg.ims.material.dto;

import java.math.BigDecimal;

import com.springmfg.ims.material.domain.Material;

public record MaterialDto(
        Long id,
        String materialCode,
        String name,
        String materialType,
        String grade,
        BigDecimal diameterMm,
        String uom,
        Long preferredSupplierId,
        BigDecimal minStock,
        BigDecimal reorderLevel,
        BigDecimal maxStock,
        BigDecimal standardCost,
        Integer shelfLifeDays,
        String description,
        boolean active,
        Long version) {

    public static MaterialDto from(Material m) {
        return new MaterialDto(
            m.getId(), m.getMaterialCode(), m.getName(), m.getMaterialType(),
            m.getGrade(), m.getDiameterMm(), m.getUom(),
            m.getPreferredSupplier() != null ? m.getPreferredSupplier().getId() : null,
            m.getMinStock(), m.getReorderLevel(), m.getMaxStock(),
            m.getStandardCost(), m.getShelfLifeDays(), m.getDescription(),
            m.isActive(), m.getVersion());
    }
}

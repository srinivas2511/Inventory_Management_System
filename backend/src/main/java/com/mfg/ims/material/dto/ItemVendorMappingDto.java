package com.mfg.ims.material.dto;

import java.math.BigDecimal;

import com.mfg.ims.material.domain.ItemVendorMapping;

public record ItemVendorMappingDto(
        Long id,
        Long itemId,
        Long supplierId,
        String supplierName,
        String supplierItemCode,
        String supplierItemName,
        BigDecimal lastPrice,
        BigDecimal moq,
        int leadTimeDays,
        boolean preferred
) {
    public static ItemVendorMappingDto from(ItemVendorMapping m, String supplierName) {
        return new ItemVendorMappingDto(
                m.getId(), m.getItemId(), m.getSupplierId(), supplierName,
                m.getSupplierItemCode(), m.getSupplierItemName(),
                m.getLastPrice(), m.getMoq(), m.getLeadTimeDays(), m.isPreferred());
    }
}

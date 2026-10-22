package com.mfg.ims.warehouse.dto;

import com.mfg.ims.warehouse.domain.Warehouse;

public record WarehouseDto(
        Long id,
        String warehouseCode,
        String name,
        String address,
        boolean active,
        Long version) {

    public static WarehouseDto from(Warehouse w) {
        return new WarehouseDto(w.getId(), w.getWarehouseCode(), w.getName(),
                w.getAddress(), w.isActive(), w.getVersion());
    }
}

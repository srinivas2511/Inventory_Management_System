package com.mfg.ims.warehouse.dto;

import com.mfg.ims.warehouse.domain.WarehouseLocation;

public record WarehouseLocationDto(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String locationCode,
        String name,
        String locationType,
        boolean active,
        Long version) {

    public static WarehouseLocationDto from(WarehouseLocation l) {
        return new WarehouseLocationDto(l.getId(),
                l.getWarehouse().getId(), l.getWarehouse().getWarehouseCode(),
                l.getLocationCode(), l.getName(), l.getLocationType(),
                l.isActive(), l.getVersion());
    }
}

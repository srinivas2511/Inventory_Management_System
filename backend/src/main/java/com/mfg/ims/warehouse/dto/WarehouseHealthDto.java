package com.mfg.ims.warehouse.dto;

import java.util.List;

public record WarehouseHealthDto(
        Long warehouseId,
        String warehouseCode,
        List<String> missingLocationTypes) {

    public boolean isHealthy() { return missingLocationTypes.isEmpty(); }
}

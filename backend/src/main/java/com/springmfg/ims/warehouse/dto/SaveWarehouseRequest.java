package com.springmfg.ims.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveWarehouseRequest(
        @NotBlank @Size(max = 20) String warehouseCode,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 300) String address) {
}

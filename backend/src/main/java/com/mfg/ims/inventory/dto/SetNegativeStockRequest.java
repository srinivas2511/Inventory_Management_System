package com.mfg.ims.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetNegativeStockRequest(
        @NotNull Boolean allowed,
        @NotBlank @Size(max = 300) String reason) {
}

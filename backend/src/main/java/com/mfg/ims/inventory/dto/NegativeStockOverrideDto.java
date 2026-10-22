package com.mfg.ims.inventory.dto;

/** One item (material or product) that is allowed to go negative on its own, beside the global setting. */
public record NegativeStockOverrideDto(String itemType, Long id, String code, String name, boolean allowed) {
}

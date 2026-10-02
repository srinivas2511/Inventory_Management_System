package com.springmfg.ims.inventory.event;

import com.springmfg.ims.inventory.domain.InventoryTransaction;

public record StockPostedEvent(InventoryTransaction transaction) {
}

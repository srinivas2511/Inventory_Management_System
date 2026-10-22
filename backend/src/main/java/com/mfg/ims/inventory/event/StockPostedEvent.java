package com.mfg.ims.inventory.event;

import com.mfg.ims.inventory.domain.InventoryTransaction;

public record StockPostedEvent(InventoryTransaction transaction) {
}

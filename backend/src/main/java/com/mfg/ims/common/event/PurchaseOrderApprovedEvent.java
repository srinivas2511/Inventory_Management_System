package com.mfg.ims.common.event;

/** Published inside the purchase order approval transaction. */
public record PurchaseOrderApprovedEvent(Long purchaseOrderId) {
}

package com.springmfg.ims.common.event;

/** Published inside the transaction that moves a product to a new lifecycle state. */
public record ProductLifecycleChangedEvent(Long productId, String fromStatus, String toStatus) {
}

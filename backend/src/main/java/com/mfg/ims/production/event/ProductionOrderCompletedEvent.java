package com.mfg.ims.production.event;

/** Published inside the transaction in which the last operation completes and the finished goods are received. */
public record ProductionOrderCompletedEvent(Long productionOrderId) {
}

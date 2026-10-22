package com.mfg.ims.common.event;

import java.util.Collection;

/** Published inside the ECO implementation transaction, once per ECO, with every product the ECO touched. */
public record EcoImplementedEvent(Long ecoId, String ecoNumber, Collection<Long> productIds) {
}

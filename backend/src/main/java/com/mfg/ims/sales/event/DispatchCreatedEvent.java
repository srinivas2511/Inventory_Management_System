package com.mfg.ims.sales.event;

/** Published inside the dispatch transaction; finance reacts to it after commit and invoices the dispatch. */
public record DispatchCreatedEvent(Long dispatchId, String dispatchNumber) {
}

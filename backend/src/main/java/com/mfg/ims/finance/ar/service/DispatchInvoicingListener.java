package com.mfg.ims.finance.ar.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.sales.event.DispatchCreatedEvent;

/** Invoices a dispatch once its transaction has committed, so an accounting problem can never undo the dispatch. */
@Component
public class DispatchInvoicingListener {

    private final ArReconciliationService reconciliation;

    public DispatchInvoicingListener(ArReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispatchCreated(DispatchCreatedEvent event) {
        reconciliation.attempt(event.dispatchId(), event.dispatchNumber());
    }
}

package com.springmfg.ims.finance.gl.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.insight.alert.AlertService;

/**
 * Makes an accounting step that did not complete visible. The steps run after the business transaction has committed
 * (stock and dispatches must never be blocked by accounting), so the only durable record of a failure is an alert.
 * Every method starts its own transaction: an AFTER_COMMIT listener has no usable one. Callers must catch what these
 * throw, because a failing alert write must not hide the original problem.
 */
@Component
public class AccountingFailureReporter {

    public static final String MISSING_AR_INVOICE = "MISSING_AR_INVOICE";
    public static final String MISSING_JOURNAL = "MISSING_JOURNAL";

    private static final int ALERT_MESSAGE_MAX = 300;

    private final AlertService alertService;

    public AccountingFailureReporter(AlertService alertService) {
        this.alertService = alertService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void missingArInvoice(Long dispatchId, String dispatchNumber, String reason) {
        alertService.raise(MISSING_AR_INVOICE, "HIGH", "DISPATCH", dispatchId,
                fit("No AR invoice for dispatch " + dispatchNumber + ": " + reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void arInvoiceCreated(Long dispatchId, String dispatchNumber) {
        alertService.resolveOpen(MISSING_AR_INVOICE, "DISPATCH", dispatchId,
                "AR invoice created for dispatch " + dispatchNumber);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void missingJournal(Long inventoryTransactionId, String transactionType, String transactionNumber,
                               String reason) {
        alertService.raise(MISSING_JOURNAL, "HIGH", "INVENTORY_TRANSACTION", inventoryTransactionId,
                fit("No GL journal for " + transactionType + " " + transactionNumber + ": " + reason));
    }

    private static String fit(String message) {
        return message.length() <= ALERT_MESSAGE_MAX ? message : message.substring(0, ALERT_MESSAGE_MAX - 3) + "...";
    }
}

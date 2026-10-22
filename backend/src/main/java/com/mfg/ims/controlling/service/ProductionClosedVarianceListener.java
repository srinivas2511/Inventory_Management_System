package com.mfg.ims.controlling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.production.event.ProductionOrderClosedEvent;

/**
 * Posts the variance journal after the order-close transaction has committed. Accounting problems
 * (closed period, missing GL account) must never block closing a production order; they are logged
 * and the variance can be re-run from the controlling API.
 */
@Component
public class ProductionClosedVarianceListener {

    private static final Logger log = LoggerFactory.getLogger(ProductionClosedVarianceListener.class);

    private final ProductionOrderVarianceService varianceService;

    public ProductionClosedVarianceListener(ProductionOrderVarianceService varianceService) {
        this.varianceService = varianceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderClosed(ProductionOrderClosedEvent event) {
        try {
            varianceService.calculate(event.productionOrderId());
        } catch (Exception e) {
            log.error("Variance posting failed for production order {}: {}", event.productionOrderId(), e.getMessage(), e);
        }
    }
}

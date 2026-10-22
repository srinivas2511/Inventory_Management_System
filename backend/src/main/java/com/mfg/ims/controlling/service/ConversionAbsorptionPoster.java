package com.mfg.ims.controlling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.finance.gl.service.AccountingFailureReporter;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.event.ProductionOrderCompletedEvent;
import com.mfg.ims.production.repository.ProductionOrderRepository;

/**
 * Posts the conversion-cost absorption journal after the order-completion transaction has committed. Accounting
 * problems (closed period, missing GL account) must never block completing an order or receiving its output: the
 * failure is recorded on the order's conversion-cost row, raised as a MISSING_JOURNAL alert, and retried whenever the
 * variance is calculated again.
 */
@Component
public class ConversionAbsorptionPoster {

    private static final Logger log = LoggerFactory.getLogger(ConversionAbsorptionPoster.class);

    private final OrderConversionCostService conversionService;
    private final ProductionOrderRepository orderRepo;
    private final AccountingFailureReporter failureReporter;

    public ConversionAbsorptionPoster(OrderConversionCostService conversionService, ProductionOrderRepository orderRepo,
                                      AccountingFailureReporter failureReporter) {
        this.conversionService = conversionService;
        this.orderRepo = orderRepo;
        this.failureReporter = failureReporter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(ProductionOrderCompletedEvent event) {
        postAndReport(event.productionOrderId());
    }

    /** Posts the journal; on failure records it and raises the alert instead of throwing. True when posted now. */
    public boolean postAndReport(Long productionOrderId) {
        String number = String.valueOf(productionOrderId);
        try {
            number = orderRepo.findById(productionOrderId).map(ProductionOrder::getOrderNumber).orElse(number);
            boolean posted = conversionService.post(productionOrderId);
            if (posted) {
                failureReporter.conversionCostPosted(productionOrderId, number);
            }
            return posted;
        } catch (Exception e) {
            log.error("Conversion cost of production order {} was not absorbed into WIP: {}", number, e.getMessage(), e);
            try {
                conversionService.recordFailure(productionOrderId, e.getMessage());
                failureReporter.conversionCostUnposted(productionOrderId, number, e.getMessage());
            } catch (Exception reportFailure) {
                log.error("Could not record the conversion cost failure of production order {}: {}", number,
                        reportFailure.getMessage(), reportFailure);
            }
            return false;
        }
    }
}

package com.mfg.ims.maintenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.finance.gl.service.AccountingFailureReporter;
import com.mfg.ims.maintenance.domain.MaintenanceRequest;
import com.mfg.ims.maintenance.event.MaintenanceCompletedEvent;
import com.mfg.ims.maintenance.repository.MaintenanceRequestRepository;

/**
 * The accounting step that follows completing a maintenance request. It runs after the completion has committed and
 * must never undo it, so a failure is recorded as a MAINTENANCE_COST_UNPOSTED alert and can be retried.
 */
@Component
public class MaintenanceCostPoster {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceCostPoster.class);

    private final MaintenanceCostService costService;
    private final MaintenanceRequestRepository requestRepo;
    private final AccountingFailureReporter failureReporter;

    public MaintenanceCostPoster(MaintenanceCostService costService, MaintenanceRequestRepository requestRepo,
                                 AccountingFailureReporter failureReporter) {
        this.costService = costService;
        this.requestRepo = requestRepo;
        this.failureReporter = failureReporter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(MaintenanceCompletedEvent event) {
        postAndReport(event.maintenanceRequestId());
    }

    /** Posts the labour cost; on failure raises the alert instead of throwing. Returns whether it is now posted. */
    public boolean postAndReport(Long requestId) {
        String number = String.valueOf(requestId);
        try {
            number = requestRepo.findById(requestId).map(MaintenanceRequest::getRequestNumber).orElse(number);
            costService.postLabourCost(requestId);
            failureReporter.maintenanceCostPosted(requestId, number);
            return true;
        } catch (Exception e) {
            log.error("Maintenance labour cost of request {} was not posted: {}", requestId, e.getMessage(), e);
            try {
                failureReporter.maintenanceCostUnposted(requestId, number, e.getMessage());
            } catch (Exception alertFailure) {
                log.error("Could not raise the MAINTENANCE_COST_UNPOSTED alert for request {}: {}", requestId,
                        alertFailure.getMessage(), alertFailure);
            }
            return false;
        }
    }

    /** Retries every completed request whose labour cost is not in the GL; returns how many are now posted. */
    public int repostUnposted() {
        int posted = 0;
        for (MaintenanceRequest r : requestRepo.findCompletedWithUnpostedCost()) {
            if (postAndReport(r.getId())) {
                posted++;
            }
        }
        return posted;
    }
}

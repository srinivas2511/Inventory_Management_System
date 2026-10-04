package com.springmfg.ims.finance.ar.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.finance.ar.dto.ArReconcileResultDto;
import com.springmfg.ims.finance.ar.dto.MissingArInvoiceDto;
import com.springmfg.ims.finance.ar.repository.ArInvoiceRepository;
import com.springmfg.ims.finance.gl.service.AccountingFailureReporter;
import com.springmfg.ims.sales.domain.Dispatch;

/**
 * Keeps dispatches and AR invoices in step. Invoicing happens after the dispatch commits and can fail (closed
 * period, missing GL data); this service is the single place that attempts it, records a failure as an alert, closes
 * the alert on success, and retries every dispatch that still has no invoice. It is safe to run any number of times:
 * an invoice that already exists is left alone, and the database allows one invoice per dispatch.
 */
@Service
public class ArReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ArReconciliationService.class);
    private static final int BATCH = 500;

    public enum Outcome { CREATED, ALREADY_INVOICED, FAILED }

    public record Attempt(Outcome outcome, String reason) {
    }

    private final ArService arService;
    private final ArInvoiceRepository arInvoiceRepo;
    private final AccountingFailureReporter failureReporter;
    private final AuditService auditService;

    public ArReconciliationService(ArService arService, ArInvoiceRepository arInvoiceRepo,
                                   AccountingFailureReporter failureReporter, AuditService auditService) {
        this.arService = arService;
        this.arInvoiceRepo = arInvoiceRepo;
        this.failureReporter = failureReporter;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MissingArInvoiceDto> findMissing() {
        return arInvoiceRepo.findDispatchesWithoutInvoice(PageRequest.of(0, BATCH)).stream()
                .map(d -> new MissingArInvoiceDto(d.getId(), d.getDispatchNumber(), d.getCustomerId(),
                        d.getDispatchDate()))
                .toList();
    }

    /** Never throws: the caller is either a post-commit listener or a batch loop, and neither may be interrupted. */
    public Attempt attempt(Long dispatchId, String dispatchNumber) {
        try {
            boolean existed = arInvoiceRepo.findByDispatchId(dispatchId).isPresent();
            arService.autoCreateFromDispatch(dispatchId);
            if (existed) {
                return new Attempt(Outcome.ALREADY_INVOICED, null);
            }
            closeAlert(dispatchId, dispatchNumber);
            return new Attempt(Outcome.CREATED, null);
        } catch (Exception e) {
            if (arInvoiceRepo.findByDispatchId(dispatchId).isPresent()) {
                return new Attempt(Outcome.ALREADY_INVOICED, null);
            }
            String reason = rootReason(e);
            log.error("AR invoice creation failed for dispatch {}: {}", dispatchNumber, reason, e);
            try {
                failureReporter.missingArInvoice(dispatchId, dispatchNumber, reason);
            } catch (Exception alertFailure) {
                log.error("Could not raise the MISSING_AR_INVOICE alert for dispatch {}: {}", dispatchNumber,
                        alertFailure.getMessage(), alertFailure);
            }
            return new Attempt(Outcome.FAILED, reason);
        }
    }

    public ArReconcileResultDto retryMissing() {
        List<Dispatch> missing = arInvoiceRepo.findDispatchesWithoutInvoice(PageRequest.of(0, BATCH));
        int created = 0;
        int already = 0;
        List<ArReconcileResultDto.Failure> failures = new ArrayList<>();
        for (Dispatch d : missing) {
            Attempt a = attempt(d.getId(), d.getDispatchNumber());
            switch (a.outcome()) {
                case CREATED -> created++;
                case ALREADY_INVOICED -> already++;
                case FAILED -> failures.add(
                        new ArReconcileResultDto.Failure(d.getId(), d.getDispatchNumber(), a.reason()));
            }
        }
        ArReconcileResultDto result =
                new ArReconcileResultDto(missing.size(), created, already, failures.size(), failures);
        auditService.record("AR_RECONCILE_RETRY", "AR_INVOICE", null, null,
                "attempted=" + result.attempted() + " created=" + created + " alreadyInvoiced=" + already
                        + " failed=" + result.failed(), null);
        return result;
    }

    private void closeAlert(Long dispatchId, String dispatchNumber) {
        try {
            failureReporter.arInvoiceCreated(dispatchId, dispatchNumber);
        } catch (Exception e) {
            log.warn("Could not close the MISSING_AR_INVOICE alert for dispatch {}: {}", dispatchNumber,
                    e.getMessage());
        }
    }

    private static String rootReason(Exception e) {
        String msg = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }
}

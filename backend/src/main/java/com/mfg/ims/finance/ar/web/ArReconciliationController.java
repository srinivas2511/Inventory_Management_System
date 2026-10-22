package com.mfg.ims.finance.ar.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.finance.ar.dto.ArReconcileResultDto;
import com.mfg.ims.finance.ar.dto.MissingArInvoiceDto;
import com.mfg.ims.finance.ar.service.ArReconciliationService;

@RestController
@RequestMapping("/api/ar/reconciliation")
public class ArReconciliationController {

    private final ArReconciliationService reconciliation;

    public ArReconciliationController(ArReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @GetMapping("/missing-invoices")
    @PreAuthorize("hasAnyAuthority('AR_RECONCILE','AR_MANAGE')")
    public List<MissingArInvoiceDto> missingInvoices() {
        return reconciliation.findMissing();
    }

    @PostMapping("/missing-invoices/retry")
    @PreAuthorize("hasAuthority('AR_RECONCILE')")
    public ArReconcileResultDto retry() {
        return reconciliation.retryMissing();
    }
}

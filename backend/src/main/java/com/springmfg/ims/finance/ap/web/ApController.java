package com.springmfg.ims.finance.ap.web;

import com.springmfg.ims.auth.stepup.StepUpService;
import com.springmfg.ims.finance.ap.dto.ApAgeingRow;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApMatchOverrideRequest;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.service.ApService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ap")
public class ApController {

    private final ApService apService;
    private final StepUpService stepUp;

    public ApController(ApService apService, StepUpService stepUp) {
        this.apService = apService;
        this.stepUp = stepUp;
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApInvoiceDto createInvoice(@Valid @RequestBody ApInvoiceCreateRequest req) {
        return apService.createInvoice(req);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public Page<ApInvoiceDto> listInvoices(Pageable pageable) {
        return apService.findAll(pageable);
    }

    @PutMapping("/invoices/{id}/open")
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApInvoiceDto openInvoice(@PathVariable Long id) {
        return apService.openInvoice(id);
    }

    @PostMapping("/invoices/{id}/match-override")
    @PreAuthorize("hasAuthority('AP_MATCH_OVERRIDE')")
    public ApInvoiceDto overrideMatch(@PathVariable Long id,
                                      @Valid @RequestBody ApMatchOverrideRequest req) {
        return apService.overrideMatch(id, req.reason());
    }

    @PutMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApInvoiceDto cancelInvoice(@PathVariable Long id) {
        return apService.cancelInvoice(id);
    }

    @PostMapping("/invoices/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public ApInvoiceDto recordPayment(@PathVariable Long id,
                                      @Valid @RequestBody ApPaymentRequest req) {
        stepUp.requireFresh();
        return apService.recordPayment(id, req);
    }

    @GetMapping("/ageing")
    @PreAuthorize("hasAuthority('FINANCE_REPORT')")
    public List<ApAgeingRow> ageing() {
        return apService.getAgeing();
    }
}

package com.mfg.ims.finance.advance;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/advance-receipts")
public class AdvanceReceiptController {

    private final AdvanceReceiptService service;

    public AdvanceReceiptController(AdvanceReceiptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADVANCE_RECEIPT_MANAGE')")
    public List<AdvanceReceiptDto> list(@RequestParam(required = false) Long customerId) {
        return service.list(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADVANCE_RECEIPT_MANAGE')")
    public AdvanceReceiptDto create(@Valid @RequestBody AdvanceReceiptRequest req,
                                    @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PostMapping("/{id}/apply-to-invoice")
    @PreAuthorize("hasAuthority('ADVANCE_RECEIPT_MANAGE')")
    public AdvanceReceiptDto applyToInvoice(@PathVariable Long id,
                                             @Valid @RequestBody ApplyAdvanceRequest req,
                                             @AuthenticationPrincipal CachedUser actor) {
        return service.applyToInvoice(id, req.invoiceId(), req.amount(), actor.id());
    }
}

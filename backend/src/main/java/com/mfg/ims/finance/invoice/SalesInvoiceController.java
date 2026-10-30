package com.mfg.ims.finance.invoice;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/sales-invoices")
public class SalesInvoiceController {

    private final SalesInvoiceService service;
    private final SalesInvoiceRenderer renderer;

    public SalesInvoiceController(SalesInvoiceService service, SalesInvoiceRenderer renderer) {
        this.service = service;
        this.renderer = renderer;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_INVOICE_VIEW')")
    public List<SalesInvoiceDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_INVOICE_VIEW')")
    public SalesInvoiceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALES_INVOICE_CREATE')")
    public SalesInvoiceDto create(@Valid @RequestBody SaveInvoiceRequest req,
                                  @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('SALES_INVOICE_POST')")
    public SalesInvoiceDto post(@PathVariable Long id,
                                @AuthenticationPrincipal CachedUser actor) {
        return service.post(id, actor.id());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALES_INVOICE_CANCEL')")
    public SalesInvoiceDto cancel(@PathVariable Long id,
                                  @RequestBody(required = false) Map<String, String> body,
                                  @AuthenticationPrincipal CachedUser actor) {
        String reason = body != null ? body.get("reason") : null;
        return service.cancel(id, reason, actor.id());
    }

    @PostMapping("/from-service-confirmation/{confId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALES_MANAGE')")
    public SalesInvoiceDto fromServiceConfirmation(@PathVariable Long confId,
                                                   @AuthenticationPrincipal CachedUser actor) {
        return service.createFromServiceConfirmation(confId, actor.id());
    }

    @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
    @PreAuthorize("hasAuthority('SALES_INVOICE_VIEW')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        SalesInvoiceDto inv = service.get(id);
        byte[] bytes = renderer.render(inv);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"invoice-" + inv.docNo() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}

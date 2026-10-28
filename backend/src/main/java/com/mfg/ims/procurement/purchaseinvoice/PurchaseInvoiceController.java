package com.mfg.ims.procurement.purchaseinvoice;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;

    public PurchaseInvoiceController(PurchaseInvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public List<PurchaseInvoiceDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_VIEW')")
    public PurchaseInvoiceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_CREATE')")
    public PurchaseInvoiceDto create(@Valid @RequestBody SavePurchaseInvoiceRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PURCHASE_INVOICE_POST')")
    public PurchaseInvoiceDto post(@PathVariable Long id) {
        return service.post(id);
    }
}

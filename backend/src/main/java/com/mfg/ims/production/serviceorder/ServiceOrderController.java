package com.mfg.ims.production.serviceorder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.finance.invoice.SalesInvoiceDto;
import com.mfg.ims.procurement.dto.GoodsReceiptDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/service-orders")
public class ServiceOrderController {

    private final ServiceOrderService service;

    public ServiceOrderController(ServiceOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVICE_ORDER_VIEW')")
    public List<ServiceOrderDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVICE_ORDER_VIEW')")
    public ServiceOrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_ORDER_CREATE')")
    public ServiceOrderDto create(@RequestBody CreateServiceOrderRequest req,
                                   @AuthenticationPrincipal CachedUser actor) {
        return service.create(req, actor.id());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SERVICE_ORDER_CREATE')")
    public ServiceOrderDto complete(@PathVariable Long id,
                                     @AuthenticationPrincipal CachedUser actor) {
        return service.complete(id, actor.id());
    }

    /** Confirm service delivery — creates a confirmation record. */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('SERVICE_ORDER_CREATE')")
    public ServiceConfirmationDto confirm(@PathVariable Long id,
                                          @RequestBody(required = false) ConfirmServiceRequest req,
                                          @AuthenticationPrincipal CachedUser actor) {
        return service.confirm(id, req, actor.id());
    }

    /** List all service confirmations for an order. */
    @GetMapping("/{id}/confirmations")
    @PreAuthorize("hasAuthority('SERVICE_ORDER_VIEW')")
    public List<ServiceConfirmationDto> confirmations(@PathVariable Long id) {
        return service.getConfirmations(id);
    }

    /**
     * Create a goods-receipt inward (GRN) from a service confirmation.
     * Posts stock IN as SERVICE_INWARD via InventoryPostingService.
     */
    @PostMapping("/confirmations/{confId}/inward")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
    public GoodsReceiptDto createInward(@PathVariable Long confId,
                                         @RequestBody CreateGrnFromConfRequest req,
                                         @AuthenticationPrincipal CachedUser actor) {
        return service.createInwardFromConfirmation(confId, req, actor.id());
    }

    /**
     * Create a sales invoice from a service confirmation.
     */
    @PostMapping("/confirmations/{confId}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALES_INVOICE_CREATE')")
    public SalesInvoiceDto createInvoice(@PathVariable Long confId,
                                          @RequestBody ServiceInvoiceRequest req,
                                          @AuthenticationPrincipal CachedUser actor) {
        return service.createInvoiceFromConfirmation(confId, req, actor.id());
    }
}

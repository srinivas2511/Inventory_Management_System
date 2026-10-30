package com.mfg.ims.production.serviceorder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
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
}

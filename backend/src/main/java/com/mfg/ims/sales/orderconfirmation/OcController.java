package com.mfg.ims.sales.orderconfirmation;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/order-confirmations")
public class OcController {

    private final OcService service;

    public OcController(OcService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OC_VIEW')")
    public List<OcDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OC_VIEW')")
    public OcDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OC_CREATE')")
    public OcDto create(@Valid @RequestBody SaveOcRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OC_UPDATE')")
    public OcDto update(@PathVariable Long id, @Valid @RequestBody SaveOcRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('OC_CONFIRM')")
    public OcDto confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('OC_CANCEL')")
    public OcDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('OC_SUBMIT')")
    public OcDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('OC_APPROVE')")
    public OcDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/create-work-order")
    @PreAuthorize("hasAuthority('OC_CREATE_WORKORDER')")
    public WorkOrderResultDto createWorkOrder(@PathVariable Long id) {
        return service.createWorkOrder(id);
    }
}

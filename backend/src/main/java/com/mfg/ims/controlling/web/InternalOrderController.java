package com.mfg.ims.controlling.web;

import com.mfg.ims.controlling.dto.*;
import com.mfg.ims.controlling.service.InternalOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/controlling/internal-orders")
public class InternalOrderController {

    private final InternalOrderService service;

    public InternalOrderController(InternalOrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_VIEW')")
    public List<InternalOrderDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_VIEW')")
    public InternalOrderDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_MANAGE')")
    public InternalOrderDto create(@Valid @RequestBody CreateInternalOrderRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_MANAGE')")
    public InternalOrderDto update(@PathVariable Long id, @Valid @RequestBody CreateInternalOrderRequest req) {
        return service.update(id, req);
    }

    @GetMapping("/{id}/costs")
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_VIEW')")
    public List<InternalOrderCostRow> costs(@PathVariable Long id) {
        return service.getCosts(id);
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('INTERNAL_ORDER_MANAGE')")
    public InternalOrderDto settle(@PathVariable Long id, @Valid @RequestBody SettleInternalOrderRequest req) {
        return service.settle(id, req);
    }
}

package com.springmfg.ims.sales.web;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.SalesOrderService;

@RestController
@RequestMapping("/api/sales/orders")
public class SalesOrderController {

    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    public record CancelRequest(@NotBlank String reason) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_MANAGE')")
    public SalesOrderDto create(@Valid @RequestBody CreateSalesOrderRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','SALES_MANAGE','SALES_APPROVE')")
    public SalesOrderDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','SALES_MANAGE','SALES_APPROVE')")
    public Page<SalesOrderDto> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(customerId, status, from, to, pageable);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_MANAGE')")
    public SalesOrderDto confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SALES_APPROVE')")
    public SalesOrderDto approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('SALES_MANAGE','SALES_APPROVE')")
    public SalesOrderDto cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_MANAGE')")
    public SalesOrderDto addItem(@PathVariable Long id, @Valid @RequestBody AddSalesOrderItemRequest req) {
        return service.addItem(id, req);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_MANAGE')")
    public SalesOrderDto removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.removeItem(id, itemId);
    }
}

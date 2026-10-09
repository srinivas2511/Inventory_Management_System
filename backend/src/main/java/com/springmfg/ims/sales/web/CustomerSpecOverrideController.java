package com.springmfg.ims.sales.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.sales.dto.CustomerSpecOverrideDto;
import com.springmfg.ims.sales.dto.SaveCustomerSpecOverrideRequest;
import com.springmfg.ims.sales.service.CustomerSpecOverrideService;

@RestController
@RequestMapping("/api/customers/{customerId}/product-specs/{productId}/overrides")
public class CustomerSpecOverrideController {

    private final CustomerSpecOverrideService service;

    public CustomerSpecOverrideController(CustomerSpecOverrideService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','SALES_UPDATE','QUALITY_VIEW')")
    public List<CustomerSpecOverrideDto> list(@PathVariable Long customerId, @PathVariable Long productId) {
        return service.list(customerId, productId);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('SALES_UPDATE','SALES_CREATE')")
    public CustomerSpecOverrideDto save(@PathVariable Long customerId, @PathVariable Long productId,
                                        @Valid @RequestBody SaveCustomerSpecOverrideRequest req) {
        return service.save(customerId, productId, req);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('SALES_UPDATE')")
    public void deactivate(@PathVariable Long customerId, @PathVariable Long productId,
                           @RequestParam String parameterName) {
        service.deactivate(customerId, productId, parameterName);
    }
}

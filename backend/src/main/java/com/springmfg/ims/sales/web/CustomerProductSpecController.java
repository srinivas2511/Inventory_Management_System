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
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.sales.dto.CustomerProductSpecDto;
import com.springmfg.ims.sales.dto.SaveCustomerProductSpecRequest;
import com.springmfg.ims.sales.service.CustomerProductSpecService;

@RestController
@RequestMapping("/api/customers/{customerId}/product-specs")
public class CustomerProductSpecController {

    private final CustomerProductSpecService service;

    public CustomerProductSpecController(CustomerProductSpecService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','SALES_UPDATE')")
    public List<CustomerProductSpecDto> listByCustomer(@PathVariable Long customerId) {
        return service.findByCustomer(customerId);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyAuthority('SALES_VIEW','SALES_CREATE','SALES_UPDATE')")
    public CustomerProductSpecDto getByCustomerAndProduct(
            @PathVariable Long customerId, @PathVariable Long productId) {
        return service.findByCustomerAndProduct(customerId, productId);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyAuthority('SALES_UPDATE','SALES_CREATE')")
    public CustomerProductSpecDto save(
            @PathVariable Long customerId, @PathVariable Long productId,
            @Valid @RequestBody SaveCustomerProductSpecRequest req) {
        return service.save(customerId, productId, req);
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAuthority('SALES_UPDATE')")
    public void deactivate(@PathVariable Long customerId, @PathVariable Long productId) {
        service.deactivate(customerId, productId);
    }
}

package com.springmfg.ims.sales.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.partner.dto.ApprovedItemDto;
import com.springmfg.ims.partner.dto.SetApprovedItemsRequest;
import com.springmfg.ims.sales.service.CustomerApprovedProductService;

@RestController
@RequestMapping("/api/customers/{customerId}/approved-products")
public class CustomerApprovedProductController {

    private final CustomerApprovedProductService service;

    public CustomerApprovedProductController(CustomerApprovedProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CUSTOMER_MANAGE','MASTERDATA_MANAGE','SALES_VIEW')")
    public List<ApprovedItemDto> list(@PathVariable Long customerId) {
        return service.list(customerId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public List<ApprovedItemDto> replace(@PathVariable Long customerId, @Valid @RequestBody SetApprovedItemsRequest req) {
        return service.replace(customerId, req.ids());
    }
}

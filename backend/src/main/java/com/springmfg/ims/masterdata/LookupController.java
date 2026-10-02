package com.springmfg.ims.masterdata;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Small typeahead lists for dropdowns in other people's forms (DESIGN.md section 8.4). They return only
 * {@code id, code, name} of <em>active</em> records, so a role may fill a picker without being able to read the
 * full records.
 */
@RestController
@RequestMapping("/api/lookups")
@Tag(name = "Lookups")
@SecurityRequirement(name = "bearerAuth")
public class LookupController {

    private final CustomerService customers;

    LookupController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyAuthority('CUSTOMER_MANAGE', 'SALES_VIEW', 'PRODUCT_VIEW')")
    @Operation(summary = "Active customers for a picker (search q matches code or name; at most 50)")
    public List<PartnerDtos.PartnerRef> customers(@RequestParam(required = false) String q) {
        return customers.lookup(q);
    }
}

package com.springmfg.ims.masterdata;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Customers. DELETE deactivates (never deletes); deactivation only blocks new documents. */
@RestController
@RequestMapping("/api/customers")
@Validated
@Tag(name = "Customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CUSTOMER_MANAGE', 'SALES_VIEW')")
    @Operation(summary = "List customers (search q, filter active, page, sort by code/name/email/gstNumber/active/createdAt)")
    public PageResponse<PartnerDtos.PartnerSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, Pageable pageable) {
        return service.list(q, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('CUSTOMER_MANAGE', 'SALES_VIEW')")
    public PartnerDtos.PartnerResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public ResponseEntity<PartnerDtos.PartnerResponse> create(@Valid @RequestBody PartnerDtos.CreatePartnerRequest request) {
        PartnerDtos.PartnerResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public PartnerDtos.PartnerResponse update(@PathVariable long id, @Valid @RequestBody PartnerDtos.UpdatePartnerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public PartnerDtos.PartnerResponse activate(@PathVariable long id) {
        return service.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    @Operation(summary = "Deactivate the customer (never deletes)")
    public ResponseEntity<Void> deactivate(@PathVariable long id, @RequestParam(required = false) @Size(max = 500) String reason) {
        service.deactivate(id, reason);
        return ResponseEntity.noContent().build();
    }
}

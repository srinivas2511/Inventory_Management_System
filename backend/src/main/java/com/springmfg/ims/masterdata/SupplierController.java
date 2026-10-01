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

/** Suppliers. DELETE deactivates (never deletes); deactivation only blocks new documents. */
@RestController
@RequestMapping("/api/suppliers")
@Validated
@Tag(name = "Suppliers")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierService service;

    SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPPLIER_MANAGE', 'PURCHASE_VIEW')")
    @Operation(summary = "List suppliers (search q, filter active, page, sort by code/name/email/gstNumber/active/createdAt)")
    public PageResponse<PartnerDtos.PartnerSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, Pageable pageable) {
        return service.list(q, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SUPPLIER_MANAGE', 'PURCHASE_VIEW')")
    public PartnerDtos.PartnerResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public ResponseEntity<PartnerDtos.PartnerResponse> create(@Valid @RequestBody PartnerDtos.CreatePartnerRequest request) {
        PartnerDtos.PartnerResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public PartnerDtos.PartnerResponse update(@PathVariable long id, @Valid @RequestBody PartnerDtos.UpdatePartnerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    public PartnerDtos.PartnerResponse activate(@PathVariable long id) {
        return service.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
    @Operation(summary = "Deactivate the supplier (never deletes)")
    public ResponseEntity<Void> deactivate(@PathVariable long id, @RequestParam(required = false) @Size(max = 500) String reason) {
        service.deactivate(id, reason);
        return ResponseEntity.noContent().build();
    }
}

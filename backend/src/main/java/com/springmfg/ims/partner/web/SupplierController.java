package com.springmfg.ims.partner.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.partner.dto.SaveSupplierRequest;
import com.springmfg.ims.partner.dto.SupplierDto;
import com.springmfg.ims.partner.service.SupplierService;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MASTER_SUPPLIER_VIEW','MASTER_SUPPLIER_MANAGE')")
    public Page<SupplierDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('MASTER_SUPPLIER_VIEW','MASTER_SUPPLIER_MANAGE')")
    public SupplierDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_SUPPLIER_MANAGE')")
    public SupplierDto create(@Valid @RequestBody SaveSupplierRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_SUPPLIER_MANAGE')")
    public SupplierDto update(@PathVariable Long id, @Valid @RequestBody SaveSupplierRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('MASTER_SUPPLIER_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('MASTER_SUPPLIER_MANAGE')")
    public SupplierDto reactivate(@PathVariable Long id) {
        service.reactivate(id);
        return service.findById(id);
    }
}

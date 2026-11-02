package com.mfg.ims.procurement.vendortype;

import java.util.List;

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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procurement/vendor-types")
@Tag(name = "Vendor Types")
public class VendorTypeController {

    private final VendorTypeService service;

    public VendorTypeController(VendorTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VENDOR_TYPE_VIEW','VENDOR_TYPE_MANAGE')")
    public List<VendorTypeDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VENDOR_TYPE_VIEW','VENDOR_TYPE_MANAGE')")
    public VendorTypeDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VENDOR_TYPE_MANAGE')")
    public VendorTypeDto create(@Valid @RequestBody SaveVendorTypeRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VENDOR_TYPE_MANAGE')")
    public VendorTypeDto update(@PathVariable Long id, @Valid @RequestBody SaveVendorTypeRequest req) {
        return service.update(id, req);
    }
}

package com.mfg.ims.product.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.product.dto.AttributeDefinitionAdminDto;
import com.mfg.ims.product.dto.SaveAttributeDefinitionRequest;
import com.mfg.ims.product.service.AttributeDefinitionAdminService;

@RestController
@RequestMapping("/api/admin/attribute-definitions")
public class AttributeDefinitionAdminController {

    private final AttributeDefinitionAdminService service;

    public AttributeDefinitionAdminController(AttributeDefinitionAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATTRIBUTE_MANAGE')")
    public List<AttributeDefinitionAdminDto> list(@RequestParam(required = false) String productType) {
        return service.list(productType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTRIBUTE_MANAGE')")
    public AttributeDefinitionAdminDto create(@Valid @RequestBody SaveAttributeDefinitionRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTRIBUTE_MANAGE')")
    public AttributeDefinitionAdminDto update(@PathVariable Long id,
            @Valid @RequestBody SaveAttributeDefinitionRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ATTRIBUTE_MANAGE')")
    public AttributeDefinitionAdminDto deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ATTRIBUTE_MANAGE')")
    public AttributeDefinitionAdminDto activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }
}

package com.mfg.ims.production.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.production.dto.ProductionOrderTemplateDto;
import com.mfg.ims.production.dto.SaveTemplateRequest;
import com.mfg.ims.production.service.ProductionOrderTemplateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production/templates")
public class ProductionOrderTemplateController {

    private final ProductionOrderTemplateService service;

    public ProductionOrderTemplateController(ProductionOrderTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD_TEMPLATE_VIEW')")
    public List<ProductionOrderTemplateDto> list() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROD_TEMPLATE_VIEW')")
    public ProductionOrderTemplateDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROD_TEMPLATE_MANAGE')")
    public ProductionOrderTemplateDto create(@Valid @RequestBody SaveTemplateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROD_TEMPLATE_MANAGE')")
    public ProductionOrderTemplateDto update(@PathVariable Long id,
                                              @Valid @RequestBody SaveTemplateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PROD_TEMPLATE_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}

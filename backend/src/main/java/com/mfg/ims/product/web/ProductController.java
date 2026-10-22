package com.mfg.ims.product.web;

import java.util.List;

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

import com.mfg.ims.product.dto.ProductDto;
import com.mfg.ims.product.dto.SaveProductRequest;
import com.mfg.ims.product.dto.SpringAttributeDefinitionDto;
import com.mfg.ims.product.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public Page<ProductDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String springType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "productCode", direction = org.springframework.data.domain.Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, springType, status, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ProductDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ProductDto create(@Valid @RequestBody SaveProductRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody SaveProductRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductDto activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ProductDto obsolete(@PathVariable Long id) {
        return service.obsolete(id);
    }

    @GetMapping("/attributes/{springType}")
    @PreAuthorize("isAuthenticated()")
    public List<SpringAttributeDefinitionDto> attributeDefinitions(@PathVariable String springType) {
        return service.getAttributeDefinitions(springType);
    }
}

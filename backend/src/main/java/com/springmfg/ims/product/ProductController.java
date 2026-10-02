package com.springmfg.ims.product;

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

/** Spring products. {@code GET /api/products/{id}/bom} arrives with the BOM module (Phase 4). */
@RestController
@RequestMapping("/api/products")
@Validated
@Tag(name = "Products")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService service;

    ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Operation(summary = "List products (search q; filter springType, status, active, customerId, materialId; page; sort)")
    public PageResponse<ProductDtos.ProductSummary> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) SpringType springType, @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long materialId, Pageable pageable) {
        return service.list(q, springType, status, active, customerId, materialId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ProductDtos.ProductResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    @Operation(summary = "Create a product as a DRAFT; required attributes are enforced when it is activated")
    public ResponseEntity<ProductDtos.ProductResponse> create(@Valid @RequestBody ProductDtos.ProductRequest request) {
        ProductDtos.ProductResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "Replace the product's editable fields (needs the current version); never changes status")
    public ProductDtos.ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductDtos.ProductRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "DRAFT or OBSOLETE to ACTIVE; 400 listing every missing required attribute")
    public ProductDtos.ProductResponse activate(@PathVariable long id) {
        return service.activate(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "Make the product obsolete (never deletes)")
    public ResponseEntity<Void> obsolete(@PathVariable long id, @RequestParam(required = false) @Size(max = 500) String reason) {
        service.obsolete(id, reason);
        return ResponseEntity.noContent().build();
    }
}

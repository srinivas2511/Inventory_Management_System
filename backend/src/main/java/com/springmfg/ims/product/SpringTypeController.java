package com.springmfg.ims.product;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The attribute catalogue the product form and validator are generated from. */
@RestController
@RequestMapping("/api/spring-types")
@Tag(name = "Spring types")
@SecurityRequirement(name = "bearerAuth")
public class SpringTypeController {

    private final ProductService service;

    SpringTypeController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Operation(summary = "The seven spring types with their attribute counts")
    public List<ProductDtos.SpringTypeResponse> types() {
        return service.springTypes();
    }

    @GetMapping("/{type}/attributes")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    @Operation(summary = "Attribute definitions of a spring type, in display order. storage: CORE = a top-level product field, "
            + "SPECIFICATIONS = an entry of the specifications map")
    public List<ProductDtos.AttributeResponse> attributes(@PathVariable SpringType type) {
        return service.attributes(type);
    }
}

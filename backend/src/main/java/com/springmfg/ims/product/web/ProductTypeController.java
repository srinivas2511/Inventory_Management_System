package com.springmfg.ims.product.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.product.dto.ProductTypeDto;
import com.springmfg.ims.product.service.ProductTypeService;

/** Active product types (spring types plus any enabled vertical's types); the product screens' type choices. */
@RestController
@RequestMapping("/api/product-types")
public class ProductTypeController {

    private final ProductTypeService service;

    public ProductTypeController(ProductTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ProductTypeDto> list() {
        return service.listActive();
    }
}

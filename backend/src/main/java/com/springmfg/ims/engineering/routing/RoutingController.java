package com.springmfg.ims.engineering.routing;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products/{productId}/routing")
public class RoutingController {

    private final RoutingService service;

    public RoutingController(RoutingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ProductRoutingDto> get(@PathVariable Long productId) {
        return service.getForProduct(productId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROUTING_MANAGE')")
    public List<ProductRoutingDto> saveSteps(@PathVariable Long productId,
                                             @Valid @RequestBody List<SaveRoutingStepRequest> steps) {
        return service.saveSteps(productId, steps);
    }
}

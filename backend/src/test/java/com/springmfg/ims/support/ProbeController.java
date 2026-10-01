package com.springmfg.ims.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-in endpoint for full-context security tests (test sources only; never in the application jar). It needs
 * {@code PRODUCT_UPDATE}, the permission {@code PUT /api/products/{id}} will require from task 1.9.
 */
@RestController
public class ProbeController {

    @GetMapping("/api/probe/secret")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String secret() {
        return "secret";
    }
}

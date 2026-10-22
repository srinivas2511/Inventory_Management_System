package com.mfg.ims.inventory.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.inventory.dto.NegativeStockOverrideDto;
import com.mfg.ims.inventory.dto.SetNegativeStockRequest;
import com.mfg.ims.inventory.service.NegativeStockOverrideService;

@RestController
@RequestMapping("/api/inventory/negative-stock-overrides")
public class NegativeStockOverrideController {

    private final NegativeStockOverrideService service;

    public NegativeStockOverrideController(NegativeStockOverrideService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_NEGATIVE_OVERRIDE')")
    public List<NegativeStockOverrideDto> list() {
        return service.list();
    }

    @PutMapping("/materials/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_NEGATIVE_OVERRIDE')")
    public NegativeStockOverrideDto setMaterial(@PathVariable Long id, @Valid @RequestBody SetNegativeStockRequest req) {
        return service.setForMaterial(id, req.allowed(), req.reason());
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_NEGATIVE_OVERRIDE')")
    public NegativeStockOverrideDto setProduct(@PathVariable Long id, @Valid @RequestBody SetNegativeStockRequest req) {
        return service.setForProduct(id, req.allowed(), req.reason());
    }
}

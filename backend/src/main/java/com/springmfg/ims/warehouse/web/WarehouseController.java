package com.springmfg.ims.warehouse.web;

import java.util.List;

import jakarta.validation.Valid;

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

import com.springmfg.ims.warehouse.dto.SaveLocationRequest;
import com.springmfg.ims.warehouse.dto.SaveWarehouseRequest;
import com.springmfg.ims.warehouse.dto.WarehouseDto;
import com.springmfg.ims.warehouse.dto.WarehouseHealthDto;
import com.springmfg.ims.warehouse.dto.WarehouseLocationDto;
import com.springmfg.ims.warehouse.service.WarehouseService;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','WAREHOUSE_MANAGE')")
    public List<WarehouseDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        return service.search(q, active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','WAREHOUSE_MANAGE')")
    public WarehouseDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseDto create(@Valid @RequestBody SaveWarehouseRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public WarehouseDto update(@PathVariable Long id, @Valid @RequestBody SaveWarehouseRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    // ── Locations ─────────────────────────────────────────────────────────────

    @GetMapping("/{warehouseId}/locations")
    @PreAuthorize("hasAnyAuthority('INVENTORY_VIEW','WAREHOUSE_MANAGE','LOCATION_MANAGE')")
    public List<WarehouseLocationDto> listLocations(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) Boolean active) {
        return service.listLocations(warehouseId, active);
    }

    @PostMapping("/{warehouseId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public WarehouseLocationDto addLocation(
            @PathVariable Long warehouseId,
            @Valid @RequestBody SaveLocationRequest req) {
        return service.addLocation(warehouseId, req);
    }

    @PutMapping("/{warehouseId}/locations/{locationId}")
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public WarehouseLocationDto updateLocation(
            @PathVariable Long warehouseId,
            @PathVariable Long locationId,
            @Valid @RequestBody SaveLocationRequest req) {
        return service.updateLocation(warehouseId, locationId, req);
    }

    @DeleteMapping("/{warehouseId}/locations/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LOCATION_MANAGE')")
    public void deactivateLocation(@PathVariable Long warehouseId, @PathVariable Long locationId) {
        service.deactivateLocation(warehouseId, locationId);
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_MANAGE','LOCATION_MANAGE')")
    public List<WarehouseHealthDto> healthCheck() {
        return service.healthCheck();
    }
}

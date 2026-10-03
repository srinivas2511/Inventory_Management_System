package com.springmfg.ims.wms.web;

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

import com.springmfg.ims.wms.dto.WmsDtos.SaveZoneRequest;
import com.springmfg.ims.wms.dto.WmsDtos.ZoneDto;
import com.springmfg.ims.wms.service.BinService;

@RestController
@RequestMapping("/api/wms/zones")
public class WmsZoneController {

    private final BinService service;

    public WmsZoneController(BinService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('WMS_VIEW')")
    public List<ZoneDto> list(@RequestParam Long warehouseId) {
        return service.listZones(warehouseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public ZoneDto create(@Valid @RequestBody SaveZoneRequest req) {
        return service.createZone(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BIN_MANAGE')")
    public ZoneDto update(@PathVariable Long id, @Valid @RequestBody SaveZoneRequest req) {
        return service.updateZone(id, req);
    }
}

package com.mfg.ims.production.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.production.dto.DowntimeDto;
import com.mfg.ims.production.dto.RecordDowntimeRequest;
import com.mfg.ims.production.service.ProductionDowntimeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production/downtime")
public class ProductionDowntimeController {

    private final ProductionDowntimeService service;

    public ProductionDowntimeController(ProductionDowntimeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOWNTIME_RECORD')")
    public DowntimeDto record(@Valid @RequestBody RecordDowntimeRequest req) {
        return service.record(req);
    }

    @PutMapping("/{id}/end")
    @PreAuthorize("hasAuthority('DOWNTIME_RECORD')")
    public DowntimeDto end(@PathVariable Long id) {
        return service.endDowntime(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<DowntimeDto> list() {
        return service.list();
    }
}

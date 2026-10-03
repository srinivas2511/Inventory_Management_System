package com.springmfg.ims.mrp.web;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.springmfg.ims.mrp.dto.DemandForecastDto;
import com.springmfg.ims.mrp.dto.SaveDemandForecastRequest;
import com.springmfg.ims.mrp.service.DemandForecastService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mrp/forecasts")
public class DemandForecastController {

    private final DemandForecastService service;

    public DemandForecastController(DemandForecastService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MRP_VIEW')")
    public Page<DemandForecastDto> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50, sort = "targetDate") Pageable pageable) {
        return service.search(productId, from, to, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public DemandForecastDto create(@Valid @RequestBody SaveDemandForecastRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public DemandForecastDto update(@PathVariable Long id, @Valid @RequestBody SaveDemandForecastRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DEMAND_PLAN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

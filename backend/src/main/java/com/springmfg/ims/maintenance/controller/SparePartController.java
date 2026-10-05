package com.springmfg.ims.maintenance.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.springmfg.ims.maintenance.dto.SparePartDtos.SaveSparePartRequest;
import com.springmfg.ims.maintenance.dto.SparePartDtos.SparePartDto;
import com.springmfg.ims.maintenance.service.SparePartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/spare-parts")
public class SparePartController {

    private final SparePartService service;

    public SparePartController(SparePartService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SPARE_PART_VIEW')")
    public Page<SparePartDto> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Boolean active,
                                   @PageableDefault(size = 50, sort = "sparePartCode") Pageable pageable) {
        return service.list(q, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SPARE_PART_VIEW')")
    public SparePartDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SPARE_PART_MANAGE')")
    public SparePartDto create(@Valid @RequestBody SaveSparePartRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SPARE_PART_MANAGE')")
    public SparePartDto update(@PathVariable Long id, @Valid @RequestBody SaveSparePartRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SPARE_PART_MANAGE')")
    public SparePartDto deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}

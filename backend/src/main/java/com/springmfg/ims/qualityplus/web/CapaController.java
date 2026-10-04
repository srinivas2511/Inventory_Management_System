package com.springmfg.ims.qualityplus.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import com.springmfg.ims.qualityplus.domain.CapaSourceType;
import com.springmfg.ims.qualityplus.domain.CapaStatus;
import com.springmfg.ims.qualityplus.dto.CapaDtos.AdvanceCapaRequest;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CapaDto;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CapaSummaryDto;
import com.springmfg.ims.qualityplus.dto.CapaDtos.CloseCapaRequest;
import com.springmfg.ims.qualityplus.dto.CapaDtos.SaveCapaRequest;
import com.springmfg.ims.qualityplus.service.CapaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/capa")
public class CapaController {

    private final CapaService service;

    public CapaController(CapaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','CAPA_MANAGE')")
    public Page<CapaSummaryDto> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) CapaStatus status,
                                     @RequestParam(required = false) CapaSourceType source,
                                     @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable) {
        return service.search(q, status, source, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','CAPA_MANAGE')")
    public CapaDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto create(@Valid @RequestBody SaveCapaRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto update(@PathVariable Long id, @Valid @RequestBody SaveCapaRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto advance(@PathVariable Long id, @Valid @RequestBody AdvanceCapaRequest req) {
        return service.advance(id, req);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('CAPA_CLOSE')")
    public CapaDto close(@PathVariable Long id, @Valid @RequestBody CloseCapaRequest req) {
        return service.close(id, req);
    }

    @PostMapping("/from-rejection/{rejectionId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto fromRejection(@PathVariable Long rejectionId) {
        return service.fromRejection(rejectionId);
    }

    @PostMapping("/from-spc-point/{pointId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto fromSpcPoint(@PathVariable Long pointId) {
        return service.fromSpcPoint(pointId);
    }

    @PostMapping("/from-complaint/{complaintId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAPA_MANAGE')")
    public CapaDto fromComplaint(@PathVariable Long complaintId) {
        return service.fromComplaint(complaintId);
    }
}

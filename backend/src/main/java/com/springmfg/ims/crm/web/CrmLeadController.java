package com.springmfg.ims.crm.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.crm.dto.ConvertLeadRequest;
import com.springmfg.ims.crm.dto.ConvertLeadResultDto;
import com.springmfg.ims.crm.dto.DisqualifyLeadRequest;
import com.springmfg.ims.crm.dto.LeadDto;
import com.springmfg.ims.crm.dto.SaveLeadRequest;
import com.springmfg.ims.crm.service.CrmLeadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/leads")
public class CrmLeadController {

    private final CrmLeadService service;

    public CrmLeadController(CrmLeadService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<LeadDto> list(@RequestParam(required = false) String status,
                              @RequestParam(required = false) Long ownerId,
                              @RequestParam(required = false) String q,
                              @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, ownerId, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public LeadDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public LeadDto create(@Valid @RequestBody SaveLeadRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public LeadDto update(@PathVariable Long id, @Valid @RequestBody SaveLeadRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/contact")
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public LeadDto markContacted(@PathVariable Long id) {
        return service.markContacted(id);
    }

    @PostMapping("/{id}/qualify")
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public LeadDto qualify(@PathVariable Long id) {
        return service.qualify(id);
    }

    @PostMapping("/{id}/disqualify")
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public LeadDto disqualify(@PathVariable Long id, @Valid @RequestBody DisqualifyLeadRequest req) {
        return service.disqualify(id, req.reason());
    }

    @PostMapping("/{id}/convert-to-opportunity")
    @PreAuthorize("hasAuthority('LEAD_MANAGE')")
    public ConvertLeadResultDto convert(@PathVariable Long id, @Valid @RequestBody(required = false) ConvertLeadRequest req) {
        return service.convertToOpportunity(id, req);
    }
}

package com.mfg.ims.crm.web;

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

import com.mfg.ims.crm.dto.AdvanceOpportunityRequest;
import com.mfg.ims.crm.dto.OpportunityDto;
import com.mfg.ims.crm.dto.SaveOpportunityRequest;
import com.mfg.ims.crm.service.CrmOpportunityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/opportunities")
public class CrmOpportunityController {

    private final CrmOpportunityService service;

    public CrmOpportunityController(CrmOpportunityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<OpportunityDto> list(@RequestParam(required = false) String stage,
                                     @RequestParam(required = false) Long customerId,
                                     @RequestParam(required = false) Long ownerId,
                                     @RequestParam(required = false) String q,
                                     @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(stage, customerId, ownerId, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public OpportunityDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto create(@Valid @RequestBody SaveOpportunityRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto update(@PathVariable Long id, @Valid @RequestBody SaveOpportunityRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto advance(@PathVariable Long id, @Valid @RequestBody AdvanceOpportunityRequest req) {
        return service.advance(id, req);
    }
}

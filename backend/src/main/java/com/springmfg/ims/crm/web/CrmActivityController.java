package com.springmfg.ims.crm.web;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import com.springmfg.ims.crm.dto.ActivityDto;
import com.springmfg.ims.crm.dto.SaveActivityRequest;
import com.springmfg.ims.crm.service.CrmActivityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/activities")
public class CrmActivityController {

    private final CrmActivityService service;

    public CrmActivityController(CrmActivityService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public Page<ActivityDto> list(@RequestParam(required = false) Long customerId,
                                  @RequestParam(required = false) Long contactId,
                                  @RequestParam(required = false) Long leadId,
                                  @RequestParam(required = false) Long opportunityId,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                  @PageableDefault(size = 50, sort = "activityDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(customerId, contactId, leadId, opportunityId, type, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CRM_VIEW')")
    public ActivityDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public ActivityDto create(@Valid @RequestBody SaveActivityRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public ActivityDto update(@PathVariable Long id, @Valid @RequestBody SaveActivityRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CUSTOMER_ACTIVITY')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

package com.springmfg.ims.qualityplus.web;

import java.util.List;

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

import com.springmfg.ims.insight.traceability.TraceabilityDto;
import com.springmfg.ims.qualityplus.domain.ComplaintStatus;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.ComplaintDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.ComplaintSummaryDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.LookupDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.SaveComplaintRequest;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.TransitionComplaintRequest;
import com.springmfg.ims.qualityplus.service.ComplaintService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService service;

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','COMPLAINT_MANAGE')")
    public Page<ComplaintSummaryDto> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) ComplaintStatus status,
                                          @PageableDefault(size = 50, sort = "createdAt",
                                                  direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, status, pageable);
    }

    @GetMapping("/lookups/customers")
    @PreAuthorize("hasAuthority('COMPLAINT_MANAGE')")
    public List<LookupDto> customerLookup(@RequestParam(required = false) String q) {
        return service.customerLookup(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','COMPLAINT_MANAGE')")
    public ComplaintDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAnyAuthority('QUALITY_VIEW','COMPLAINT_MANAGE')")
    public TraceabilityDto trace(@PathVariable Long id) {
        return service.trace(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPLAINT_MANAGE')")
    public ComplaintDto create(@Valid @RequestBody SaveComplaintRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPLAINT_MANAGE')")
    public ComplaintDto update(@PathVariable Long id, @Valid @RequestBody SaveComplaintRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('COMPLAINT_MANAGE')")
    public ComplaintDto transition(@PathVariable Long id, @Valid @RequestBody TransitionComplaintRequest req) {
        return service.transition(id, req);
    }
}

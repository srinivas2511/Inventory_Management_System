package com.mfg.ims.sales.challan;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/delivery-challans")
public class ChallanController {

    private final ChallanService service;

    public ChallanController(ChallanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CHALLAN_VIEW')")
    public List<ChallanDto> list() {
        return service.list(1L);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CHALLAN_VIEW')")
    public ChallanDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CHALLAN_CREATE')")
    public ChallanDto create(@Valid @RequestBody SaveChallanRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('CHALLAN_ISSUE')")
    public ChallanDto issue(@PathVariable Long id) {
        return service.issue(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CHALLAN_RETURN')")
    public ChallanDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}

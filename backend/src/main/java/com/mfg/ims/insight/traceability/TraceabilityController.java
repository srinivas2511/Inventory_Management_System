package com.mfg.ims.insight.traceability;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traceability")
public class TraceabilityController {

    private final TraceabilityService service;

    public TraceabilityController(TraceabilityService service) {
        this.service = service;
    }

    @GetMapping("/material-batch/{id}")
    @PreAuthorize("hasAuthority('TRACEABILITY_VIEW')")
    public TraceabilityDto traceMaterial(@PathVariable Long id) {
        return service.traceMaterialBatch(id);
    }

    @GetMapping("/fg-batch/{id}")
    @PreAuthorize("hasAuthority('TRACEABILITY_VIEW')")
    public TraceabilityDto traceFg(@PathVariable Long id) {
        return service.traceFgBatch(id);
    }
}

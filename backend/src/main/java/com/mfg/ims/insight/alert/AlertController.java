package com.mfg.ims.insight.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public Page<AlertDto> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String alertType,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.search(status, severity, alertType, pageable);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public AlertCountDto count() {
        return service.count();
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public AlertDto acknowledge(@PathVariable Long id,
                                @RequestParam(required = false) String note) {
        return service.acknowledge(id, note);
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public AlertDto resolve(@PathVariable Long id,
                            @RequestParam(required = false) String note) {
        return service.resolve(id, note);
    }
}

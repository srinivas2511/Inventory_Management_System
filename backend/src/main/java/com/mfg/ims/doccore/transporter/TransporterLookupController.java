package com.mfg.ims.doccore.transporter;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookups/transporters")
public class TransporterLookupController {

    private final TransporterService service;

    public TransporterLookupController(TransporterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<TransporterDto> list() {
        return service.list();
    }
}

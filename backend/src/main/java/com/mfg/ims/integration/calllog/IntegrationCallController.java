package com.mfg.ims.integration.calllog;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/integration/calls")
public class IntegrationCallController {

    private final IntegrationCallService service;

    public IntegrationCallController(IntegrationCallService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    public List<IntegrationCallDto> list(@RequestParam(required = false) String connector) {
        return service.list(connector);
    }
}

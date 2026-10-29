package com.mfg.ims.analytics.bi;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bi")
public class BiController {

    private final BiService service;

    public BiController(BiService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BI_VIEW')")
    public List<String> listAreas() {
        return service.listAreas();
    }

    @GetMapping("/{area}")
    @PreAuthorize("hasAuthority('BI_VIEW')")
    public BiAreaDto getArea(@PathVariable String area) {
        return service.getArea(area);
    }
}

package com.mfg.ims.doccore.search;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GLOBAL_SEARCH')")
    public List<SearchResultDto> search(
            @RequestParam String q,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "20") int limit) {
        return service.search(1L, q, entityType, limit);
    }
}

package com.mfg.ims.production.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.production.dto.CreateIssueRequest;
import com.mfg.ims.production.dto.IssueDto;
import com.mfg.ims.production.service.MaterialIssueService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production")
public class MaterialIssueController {

    private final MaterialIssueService service;

    public MaterialIssueController(MaterialIssueService service) {
        this.service = service;
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_ISSUE')")
    public IssueDto issue(@Valid @RequestBody CreateIssueRequest req) {
        return service.issue(req);
    }
}

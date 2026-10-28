package com.mfg.ims.doccore.approvalrule;

import java.util.List;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/approval-rules")
public class ApprovalRuleController {

    private final ApprovalRuleService service;

    public ApprovalRuleController(ApprovalRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('APPROVAL_RULE_MANAGE','APPROVAL_RULE_VIEW','SETTINGS_MANAGE')")
    public List<ApprovalRuleDto> list(@RequestParam(required = false) String documentType) {
        return service.list(documentType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPROVAL_RULE_MANAGE')")
    public ApprovalRuleDto create(@Valid @RequestBody SaveApprovalRuleRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('APPROVAL_RULE_MANAGE')")
    public ApprovalRuleDto update(@PathVariable Long id, @Valid @RequestBody SaveApprovalRuleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPROVAL_RULE_MANAGE')")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}

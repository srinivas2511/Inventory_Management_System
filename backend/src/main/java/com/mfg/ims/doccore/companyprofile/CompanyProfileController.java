package com.mfg.ims.doccore.companyprofile;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/company-profile")
public class CompanyProfileController {

    private final CompanyProfileService service;

    public CompanyProfileController(CompanyProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPANY_MANAGE','SETTINGS_MANAGE')")
    public CompanyProfileDto get() {
        return service.get();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('COMPANY_MANAGE')")
    public CompanyProfileDto update(@RequestBody CompanyProfileDto req) {
        return service.update(req);
    }
}

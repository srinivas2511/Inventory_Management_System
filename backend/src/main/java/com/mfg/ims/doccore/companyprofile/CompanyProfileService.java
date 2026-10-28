package com.mfg.ims.doccore.companyprofile;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;

@Service
public class CompanyProfileService {

    private final CompanyProfileRepository repo;

    public CompanyProfileService(CompanyProfileRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public CompanyProfileDto get() {
        return repo.findByCompanyId(1L)
                .map(CompanyProfileDto::from)
                .orElseGet(() -> new CompanyProfileDto(null, 1L, "", null, null, null, null, null, null, null, null, null, 4, null));
    }

    @Transactional
    public CompanyProfileDto update(CompanyProfileDto req) {
        CompanyProfile p = repo.findByCompanyId(1L).orElseGet(() -> {
            CompanyProfile np = new CompanyProfile();
            return np;
        });
        p.setLegalName(req.legalName());
        p.setTradeName(req.tradeName());
        p.setPan(req.pan());
        p.setCin(req.cin());
        p.setDefaultPaymentTermsId(req.defaultPaymentTermsId());
        p.setFiscalYearStartMonth(req.fiscalYearStartMonth() > 0 ? req.fiscalYearStartMonth() : 4);
        p.setWebsite(req.website());
        p.setUpdatedAt(Instant.now());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            p.setUpdatedBy(u.id());
        }
        return CompanyProfileDto.from(repo.save(p));
    }
}

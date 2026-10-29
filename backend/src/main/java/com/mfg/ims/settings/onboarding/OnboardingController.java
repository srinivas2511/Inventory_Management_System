package com.mfg.ims.settings.onboarding;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.settings.onboarding.OnboardingDtos.CompleteStepRequest;
import com.mfg.ims.settings.onboarding.OnboardingDtos.OnboardingStatusDto;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/settings/onboarding")
public class OnboardingController {

    private final OnboardingService service;

    public OnboardingController(OnboardingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ONBOARDING_VIEW')")
    public OnboardingStatusDto status(@AuthenticationPrincipal CachedUser user) {
        return service.status(1L);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('ONBOARDING_MANAGE')")
    public OnboardingStatusDto complete(
            @RequestBody CompleteStepRequest req,
            @AuthenticationPrincipal CachedUser user) {
        Long userId = user != null ? user.id() : 1L;
        return service.completeStep(1L, req, userId);
    }

    @DeleteMapping("/steps/{stepKey}")
    @PreAuthorize("hasAuthority('ONBOARDING_MANAGE')")
    public OnboardingStatusDto reset(
            @PathVariable String stepKey,
            @AuthenticationPrincipal CachedUser user) {
        return service.resetStep(1L, stepKey);
    }
}

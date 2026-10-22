package com.mfg.ims.auth.web;

import com.mfg.ims.auth.stepup.StepUpService;
import com.mfg.ims.auth.stepup.StepUpService.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/step-up")
public class StepUpController {

    public record StepUpRequest(@NotBlank String credential) {}

    private final StepUpService service;

    public StepUpController(StepUpService service) {
        this.service = service;
    }

    /** Whether step-up is on and how long the caller's last re-authentication is still valid. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Status status() {
        return service.status();
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Status elevate(@Valid @RequestBody StepUpRequest request) {
        return service.elevate(request.credential());
    }
}

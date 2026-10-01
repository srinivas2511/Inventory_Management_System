package com.springmfg.ims.settings;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {

    public record UpdateSettingRequest(@NotNull @Size(max = 500) String value, @Size(max = 500) String reason) {
    }

    private final SystemSettingService service;

    SettingsController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "All system settings with their type, default and permitted values")
    public List<SettingView> list() {
        return service.list();
    }

    @PutMapping("/{key:.+}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Change one setting; validated against its type and limits, audited")
    public SettingView update(@PathVariable String key, @Valid @RequestBody UpdateSettingRequest request) {
        return service.update(key, request.value(), request.reason()).setting();
    }
}

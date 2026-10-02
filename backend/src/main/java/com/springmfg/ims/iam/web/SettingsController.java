package com.springmfg.ims.iam.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.settings.SystemSettingDto;
import com.springmfg.ims.common.settings.SystemSettingService;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SystemSettingService service;

    public SettingsController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public List<SystemSettingDto> list() {
        return service.findAll().stream().map(SystemSettingDto::from).toList();
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public SystemSettingDto update(@PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest req) {
        return SystemSettingDto.from(service.update(key, req.value()));
    }

    public record UpdateSettingRequest(@NotBlank @Size(max = 500) String value) {}
}

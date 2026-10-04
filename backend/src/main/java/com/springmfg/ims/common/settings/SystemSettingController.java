package com.springmfg.ims.common.settings;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingController {

    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public List<SystemSettingDto> findAll() {
        return service.findAll().stream().map(SystemSettingDto::from).toList();
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public SystemSettingDto update(@PathVariable String key, @RequestBody UpdateSettingRequest body) {
        return SystemSettingDto.from(service.update(key, body.value()));
    }

    public record UpdateSettingRequest(String value) {}
}

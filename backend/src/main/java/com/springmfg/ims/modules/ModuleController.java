package com.springmfg.ims.modules;

import com.springmfg.ims.modules.ModuleService.ModuleDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    public record SetActiveRequest(@NotNull Boolean active) {}

    private final ModuleService service;

    public ModuleController(ModuleService service) {
        this.service = service;
    }

    /** Every signed-in user: the navigation hides the screens of a module that is switched off. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ModuleDto> list() {
        return service.list();
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('MODULE_MANAGE')")
    public ModuleDto setActive(@PathVariable String code, @Valid @RequestBody SetActiveRequest request) {
        return service.setActive(code, request.active());
    }
}

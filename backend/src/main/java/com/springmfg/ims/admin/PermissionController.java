package com.springmfg.ims.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only: permission codes are referenced from code and created by migrations, so an API that adds or renames
 * them would only produce permissions nothing checks.
 */
@RestController
@RequestMapping("/api/permissions")
@Tag(name = "Permissions")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final RoleAdminService service;

    PermissionController(RoleAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERMISSION_MANAGE', 'ROLE_MANAGE')")
    @Operation(summary = "The permission catalogue, grouped by module")
    public List<RoleDtos.PermissionResponse> list() {
        return service.permissionCatalogue();
    }
}

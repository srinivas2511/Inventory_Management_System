package com.springmfg.ims.admin;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleAdminService service;

    RoleController(RoleAdminService service) {
        this.service = service;
    }

    /** Also readable with USER_VIEW: the user screens need the role list to assign roles. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE', 'USER_VIEW')")
    public List<RoleDtos.RoleSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE', 'USER_VIEW')")
    public RoleDtos.RoleResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Create a custom role")
    public ResponseEntity<RoleDtos.RoleResponse> create(@Valid @RequestBody RoleDtos.CreateRoleRequest request) {
        RoleDtos.RoleResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/roles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleDtos.RoleResponse update(@PathVariable long id, @Valid @RequestBody RoleDtos.UpdateRoleRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace the role's permissions; everyone holding the role must refresh their token")
    public RoleDtos.RoleResponse setPermissions(@PathVariable long id,
            @Valid @RequestBody RoleDtos.SetPermissionsRequest request) {
        return service.setPermissions(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Delete a custom role that nobody holds; system roles cannot be deleted")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

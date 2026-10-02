package com.springmfg.ims.iam.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.iam.dto.PermissionDto;
import com.springmfg.ims.iam.dto.RoleDto;
import com.springmfg.ims.iam.service.RoleAdminService;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleAdminService service;

    public RoleController(RoleAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public List<RoleDto> list() {
        return service.listRoles();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public RoleDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('IAM_USER_MANAGE')")
    public List<PermissionDto> allPermissions() {
        return service.listAllPermissions();
    }
}

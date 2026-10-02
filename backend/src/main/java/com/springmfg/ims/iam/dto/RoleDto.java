package com.springmfg.ims.iam.dto;

import java.util.List;

import com.springmfg.ims.iam.domain.Role;

public record RoleDto(
        Long id,
        String code,
        String name,
        String description,
        boolean systemRole,
        List<PermissionDto> permissions) {

    public static RoleDto from(Role r) {
        List<PermissionDto> perms = r.getPermissions() == null ? List.of()
            : r.getPermissions().stream().map(PermissionDto::from).toList();
        return new RoleDto(r.getId(), r.getCode(), r.getName(),
            r.getDescription(), r.isSystemRole(), perms);
    }
}

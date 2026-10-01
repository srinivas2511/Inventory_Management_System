package com.springmfg.ims.admin;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.springmfg.ims.iam.Permission;
import com.springmfg.ims.iam.Role;
import com.springmfg.ims.iam.User;

/** Entity to DTO mapping for the administration module. Call inside a transaction (roles are lazy). */
@Mapper(componentModel = "spring")
interface AdminMapper {

    @Mapping(target = "roles", expression = "java(sortedRoleCodes(user))")
    UserDtos.UserSummary toSummary(User user);

    @Mapping(target = "roles", expression = "java(sortedRoleCodes(user))")
    UserDtos.UserResponse toResponse(User user);

    RoleDtos.PermissionResponse toResponse(Permission permission);

    @Mapping(target = "permissionCount", expression = "java(role.getPermissions().size())")
    @Mapping(target = "userCount", source = "userCount")
    RoleDtos.RoleSummary toSummary(Role role, long userCount);

    @Mapping(target = "permissions", expression = "java(sortedPermissionCodes(role))")
    @Mapping(target = "userCount", source = "userCount")
    RoleDtos.RoleResponse toResponse(Role role, long userCount);

    default List<String> sortedRoleCodes(User user) {
        return user.roleCodes().stream().sorted().toList();
    }

    default List<String> sortedPermissionCodes(Role role) {
        return role.getPermissions().stream().map(Permission::getCode).sorted().toList();
    }
}

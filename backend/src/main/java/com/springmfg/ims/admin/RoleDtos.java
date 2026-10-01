package com.springmfg.ims.admin;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/roles} and {@code /api/permissions}. */
public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleSummary(long id, String code, String name, String description, boolean systemRole,
            int permissionCount, long userCount) {
    }

    public record RoleResponse(long id, String code, String name, String description, boolean systemRole,
            List<String> permissions, long userCount, long version) {
    }

    public record PermissionResponse(long id, String code, String module, String description) {
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,39}", message = "3-40 characters: A-Z, 0-9 and underscore") String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            @NotNull Set<@NotBlank String> permissions) {
    }

    public record UpdateRoleRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description,
            @NotNull Long version) {
    }

    public record SetPermissionsRequest(@NotNull Set<@NotBlank String> permissions, @Size(max = 500) String reason) {
    }
}

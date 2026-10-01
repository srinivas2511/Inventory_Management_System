package com.springmfg.ims.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/users}. {@code password_hash} is never part of any of them. */
public final class UserDtos {

    private UserDtos() {
    }

    /** Row of the user list. */
    public record UserSummary(long id, String username, String employeeCode, String fullName, String email,
            boolean active, List<String> roles, Instant lastLoginAt, Instant lockedUntil) {
    }

    public record UserResponse(long id, String username, String employeeCode, String fullName, String email,
            String phone, boolean active, boolean mustChangePassword, List<String> roles, Instant lastLoginAt,
            Instant lockedUntil, Instant passwordChangedAt, Instant createdAt, Instant updatedAt, long version) {
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "3-50 characters: letters, digits, dot, underscore or hyphen") String username,
            @Size(max = 20) String employeeCode,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 20) String phone,
            @NotEmpty Set<@NotBlank String> roles,
            @NotBlank @Size(max = 128) String temporaryPassword) {
    }

    /** Full replacement of the editable profile fields; the username never changes. */
    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 20) String phone,
            @Size(max = 20) String employeeCode,
            @NotNull Long version) {
    }

    public record AssignRolesRequest(@NotEmpty Set<@NotBlank String> roles, @Size(max = 500) String reason) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String temporaryPassword,
            @Size(max = 500) String reason) {
    }
}

package com.mfg.ims.iam.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.mfg.ims.iam.domain.User;

public record UserDto(
        Long id,
        String username,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        boolean active,
        boolean mustChangePassword,
        boolean locked,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Set<String> roles,
        Long version) {

    public static UserDto from(User u) {
        return new UserDto(
            u.getId(), u.getUsername(), u.getEmployeeCode(), u.getFullName(),
            u.getEmail(), u.getPhone(), u.isActive(), u.isMustChangePassword(),
            u.isLocked(), u.isLocked() ? u.getLockedUntil() : null,
            u.getLastLoginAt(), u.getPasswordChangedAt(),
            u.getRoleCodes(), u.getVersion());
    }
}

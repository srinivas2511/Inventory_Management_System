package com.springmfg.ims.auth.dto;

import java.util.Set;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    boolean mustChangePassword,
    UserInfo user
) {
    public record UserInfo(
        Long id,
        String username,
        String fullName,
        Set<String> roles,
        Set<String> permissions
    ) {}
}

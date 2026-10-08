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
        Set<String> permissions,
        boolean plantScoped
    ) {
        /** A user without plant restrictions (every user before plants existed). */
        public UserInfo(Long id, String username, String fullName, Set<String> roles, Set<String> permissions) {
            this(id, username, fullName, roles, permissions, false);
        }
    }
}

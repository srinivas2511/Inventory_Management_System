package com.springmfg.ims.auth;

import java.util.List;
import java.util.Set;

/** Server-side view of a user that authorisation decisions are made from (never trusted from the token). */
public record UserAccess(long userId, String username, String fullName, String email, boolean active, boolean mustChangePassword,
        int permissionVersion, List<String> roles, Set<String> authorities) {
}

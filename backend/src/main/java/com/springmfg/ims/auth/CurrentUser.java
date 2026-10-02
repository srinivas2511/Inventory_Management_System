package com.springmfg.ims.auth;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the signed-in user for services, the JPA auditor and (task 1.6) the audit aspect. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthentication jwt) {
            return Optional.of(jwt.getPrincipal());
        }
        return Optional.empty();
    }

    /** True if the signed-in user holds the permission (for building {@code allowedActions} in responses). */
    public static boolean hasAuthority(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthentication jwt
                && jwt.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }

    public static Optional<Long> id() {
        return get().map(AuthenticatedUser::id);
    }
}

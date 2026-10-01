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

    public static Optional<Long> id() {
        return get().map(AuthenticatedUser::id);
    }
}

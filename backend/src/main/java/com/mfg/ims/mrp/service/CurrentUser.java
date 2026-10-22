package com.mfg.ims.mrp.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;

final class CurrentUser {

    private static final long SYSTEM = -1L;

    private CurrentUser() {}

    static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        return SYSTEM;
    }
}

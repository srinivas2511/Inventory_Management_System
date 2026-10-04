package com.springmfg.ims.plm.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;

/** Reads the authenticated caller for audit columns and approver fields. */
final class PlmSecurity {

    private PlmSecurity() {
    }

    /** The authenticated user's id, or null for background work and unauthenticated callers. */
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }
}

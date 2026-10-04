package com.springmfg.ims.qualityplus.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;

/** Reads the authenticated caller for owner and approver fields. */
final class QualitySecurity {

    private QualitySecurity() {
    }

    /** The authenticated user's id, or null for background work and unauthenticated callers. */
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }

    static Long requireUserId(String action) {
        Long id = currentUserId();
        if (id == null) {
            throw new com.springmfg.ims.common.exception.BusinessRuleException(
                    com.springmfg.ims.common.exception.ErrorCode.ACCESS_DENIED,
                    "An authenticated user is required to " + action);
        }
        return id;
    }
}

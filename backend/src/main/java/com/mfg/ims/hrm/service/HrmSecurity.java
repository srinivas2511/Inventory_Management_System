package com.mfg.ims.hrm.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;

/** Reads the caller's identity and permissions for service-level decisions that a method annotation cannot make. */
public final class HrmSecurity {

    private HrmSecurity() {
    }

    /** The authenticated user's id, or null for background work and unauthenticated callers. */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CachedUser u ? u.id() : null;
    }

    public static boolean has(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Pay and bank data is visible only to HR administration and payroll. */
    public static boolean canSeeCompensation() {
        return has("EMPLOYEE_MANAGE") || has("PAYROLL_RUN");
    }
}

package com.mfg.ims.wms.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.BusinessRuleException;

/** Small helpers shared by the warehouse services. */
final class WmsSupport {

    private WmsSupport() {
    }

    /** The authenticated user's id, or null for system work such as event listeners. */
    static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u.id();
        }
        return null;
    }

    static Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    static String clip(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    static void require(boolean condition, String detail) {
        if (!condition) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, detail);
        }
    }

    static boolean sameCode(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}

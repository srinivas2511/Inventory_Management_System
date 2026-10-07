package com.springmfg.ims.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.springmfg.ims.auth.service.JwtService;
import com.springmfg.ims.auth.service.UserPermissionCache;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.Problems;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * Validates the Bearer token, checks permission_version against the live cache,
 * and builds a fully-populated Authentication for Spring Security.
 * <p>
 * A user who still has to change a temporary password is held to the password-change allow-list here, on the
 * server: every other {@code /api/**} call is answered with 403 {@code PASSWORD_CHANGE_REQUIRED}. The flag comes
 * from the permission cache, which is evicted after commit whenever the flag changes.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserPermissionCache permissionCache;
    private final ProblemDetailSecurityHandlers problemWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserPermissionCache permissionCache,
                                   ProblemDetailSecurityHandlers problemWriter) {
        this.jwtService = jwtService;
        this.permissionCache = permissionCache;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseAndValidate(header.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            int tokenPv = claims.get("pv", Integer.class);

            CachedUser user = permissionCache.load(userId);

            if (!user.active()) {
                filterChain.doFilter(request, response);
                return;
            }

            if (user.permissionVersion() != tokenPv) {
                // token is stale — treat as expired so client silently refreshes
                filterChain.doFilter(request, response);
                return;
            }

            if (user.mustChangePassword() && isBlockedWhilePasswordChangeIsPending(request)) {
                problemWriter.write(response, Problems.of(ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "You must change your temporary password before using the application."));
                return;
            }

            List<SimpleGrantedAuthority> authorities = user.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
            auth.setDetails(request);
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException | IllegalArgumentException ignored) {
            // invalid token — proceed unauthenticated
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Everything under {@code /api/} except {@code /api/auth/**} (change-password, me, logout, refresh and the
     * public sign-in endpoints) and the public ping. Non-API paths (actuator, docs) are not user data.
     */
    private static boolean isBlockedWhilePasswordChangeIsPending(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.startsWith("/api/") && !path.startsWith("/api/auth/") && !path.equals("/api/system/ping");
    }
}

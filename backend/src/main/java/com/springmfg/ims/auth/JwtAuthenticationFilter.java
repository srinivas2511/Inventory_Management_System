package com.springmfg.ims.auth;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.springmfg.ims.common.exception.ErrorCode;

/**
 * Authenticates a request from its {@code Authorization: Bearer <access token>} header (DESIGN.md section 7.3).
 * <p>
 * The token proves identity; the permissions come from {@link UserAccessService}, never from the token. A request
 * is refused as 401 when the token is invalid, the user no longer exists or is inactive, or the token's
 * {@code pv} differs from the user's current {@code permission_version} ({@code TOKEN_EXPIRED}, so the client
 * silently refreshes).
 * <p>
 * The filter does not answer failures itself. It leaves the request unauthenticated and records the reason in
 * {@link #ERROR_ATTRIBUTE}; the entry point turns that into the 401 body for protected endpoints, while public
 * endpoints (login, ping) keep working even if the client still sends a stale token.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the {@link ErrorCode} to answer with if authentication is required. */
    public static final String ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final UserAccessService access;

    public JwtAuthenticationFilter(JwtService jwt, UserAccessService access) {
        this.jwt = jwt;
        this.access = access;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            authenticate(header.substring(BEARER.length()).trim(), request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        JwtService.AccessClaims claims;
        try {
            claims = jwt.parse(token);
        } catch (JwtService.ExpiredAccessTokenException e) {
            reject(request, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            reject(request, ErrorCode.UNAUTHENTICATED);
            return;
        }
        Optional<UserAccess> found = access.find(claims.userId());
        if (found.isEmpty() || !found.get().active() || found.get().mustChangePassword()) {
            reject(request, ErrorCode.UNAUTHENTICATED);
            return;
        }
        UserAccess user = found.get();
        if (user.permissionVersion() != claims.permissionVersion()) {
            reject(request, ErrorCode.TOKEN_EXPIRED);
            return;
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.userId(), user.username(), user.roles(),
                user.permissionVersion());
        JwtAuthentication authentication = new JwtAuthentication(principal,
                user.authorities().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void reject(HttpServletRequest request, ErrorCode code) {
        SecurityContextHolder.clearContext();
        request.setAttribute(ERROR_ATTRIBUTE, code);
    }
}

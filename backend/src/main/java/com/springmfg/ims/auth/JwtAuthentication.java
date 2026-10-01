package com.springmfg.ims.auth;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** An authenticated request. Authorities are permission codes (e.g. {@code PRODUCT_UPDATE}), not roles. */
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;

    public JwtAuthentication(AuthenticatedUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.username();
    }
}

package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isEmptyWithoutAuthenticationSoSystemWorkHasNoAuditor() {
        assertThat(CurrentUser.id()).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "x", List.of(new SimpleGrantedAuthority("X"))));
        assertThat(CurrentUser.get()).isEmpty(); // only our own token type counts
    }

    @Test
    void exposesTheSignedInUser() {
        AuthenticatedUser user = new AuthenticatedUser(12, "operator1", List.of("OPERATOR"), 3);
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthentication(user, Set.of(new SimpleGrantedAuthority("PRODUCTION_EXECUTE"))));
        assertThat(CurrentUser.id()).contains(12L);
        assertThat(CurrentUser.get()).contains(user);
    }
}

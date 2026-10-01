package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import com.springmfg.ims.config.ImsSecurityProperties;
import com.springmfg.ims.iam.Role;
import com.springmfg.ims.iam.User;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");

    private static ImsSecurityProperties props(String secret) {
        return new ImsSecurityProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofMinutes(30),
                true, new ImsSecurityProperties.RateLimit(30, 5, Duration.ofMinutes(15)));
    }

    private static JwtService service(String secret, Instant now) {
        return new JwtService(props(secret), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user() {
        User user = new User("operator1", "Ravi Kumar", "ravi@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 12L);
        ReflectionTestUtils.setField(user, "permissionVersion", 3);
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("OPERATOR");
        user.getRoles().add(role);
        return user;
    }

    @Test
    void issuedTokenCarriesTheDocumentedClaims() {
        JwtService jwt = service(SECRET, T0);
        JwtService.IssuedToken token = jwt.issue(user());

        JwtService.AccessClaims claims = jwt.parse(token.value());
        assertThat(claims.userId()).isEqualTo(12L);
        assertThat(claims.username()).isEqualTo("operator1");
        assertThat(claims.roles()).containsExactly("OPERATOR");
        assertThat(claims.permissionVersion()).isEqualTo(3);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(token.expiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    void tokenIsRejectedOnceExpired() {
        String token = service(SECRET, T0).issue(user()).value();
        assertThat(service(SECRET, T0.plus(Duration.ofMinutes(14))).parse(token)).isNotNull();
        assertThatThrownBy(() -> service(SECRET, T0.plus(Duration.ofMinutes(15)).plusSeconds(1)).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = service(SECRET, T0).issue(user()).value();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"pv\":3", "\"pv\":9").replace("OPERATOR", "ADMIN").getBytes());
        assertThatThrownBy(() -> service(SECRET, T0).parse(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service("another-secret-another-secret-0123456789", T0).issue(user()).value();
        assertThatThrownBy(() -> service(SECRET, T0).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"ims\",\"sub\":\"1\",\"pv\":1,\"exp\":" + T0.plusSeconds(600).getEpochSecond() + "}").getBytes());
        assertThatThrownBy(() -> service(SECRET, T0).parse(header + "." + payload + ".")).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> service(SECRET, T0).parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToStartWithoutAStrongSecret() {
        assertThatThrownBy(() -> service(null, T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("", T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("too-short", T0)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
        assertThat(Set.of(service("x".repeat(32), T0))).hasSize(1);
    }
}

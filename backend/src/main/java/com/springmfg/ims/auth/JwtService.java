package com.springmfg.ims.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.springmfg.ims.config.ImsSecurityProperties;
import com.springmfg.ims.iam.User;

/**
 * Issues and verifies access tokens (DESIGN.md section 7.2): HS256, claims
 * {@code iss, sub, usr, roles, pv, iat, exp, jti}. The token carries identity and the permission version only;
 * the authoritative permission set is resolved server-side (task 1.4).
 */
@Component
public class JwtService {

    public static final String ISSUER = "ims";
    static final int MIN_SECRET_LENGTH = 32;

    /** What a verified token says. */
    public record AccessClaims(long userId, String username, List<String> roles, int permissionVersion,
            String tokenId, Instant expiresAt) {
    }

    /** A signed token and its expiry. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final Clock clock;
    private final Duration accessTtl;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(ImsSecurityProperties properties, Clock clock) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "ims.security.jwt-secret (env JWT_SECRET) must be set to at least 32 characters");
        }
        this.clock = clock;
        this.accessTtl = properties.accessTtl();
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO); // no clock-skew leeway
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        this.decoder = nimbus;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expires = now.plus(accessTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expires)
                .id(UUID.randomUUID().toString())
                .claim("usr", user.getUsername())
                .claim("roles", user.roleCodes().stream().sorted().toList())
                .claim("pv", user.getPermissionVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expires);
    }

    /**
     * Verifies signature, algorithm, issuer and expiry.
     *
     * @throws JwtException if the token is malformed, tampered with, expired or from another issuer
     */
    public AccessClaims parse(String token) {
        Jwt jwt = decoder.decode(token);
        try {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Long pv = jwt.getClaim("pv") instanceof Number n ? n.longValue() : null;
            if (pv == null || jwt.getSubject() == null) {
                throw new JwtException("Token is missing required claims");
            }
            return new AccessClaims(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("usr"),
                    roles == null ? List.of() : roles, pv.intValue(), jwt.getId(), jwt.getExpiresAt());
        } catch (NumberFormatException e) {
            throw new JwtException("Token subject is not a user id", e);
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }
}

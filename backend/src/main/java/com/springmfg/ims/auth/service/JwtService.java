package com.springmfg.ims.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;

    public JwtService(
            @Value("${ims.security.jwt.secret}") String secret,
            @Value("${ims.security.jwt.access-token-minutes:15}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
    }

    public String generateAccessToken(Long userId, String username, java.util.Set<String> roles, int permissionVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer("ims")
            .subject(String.valueOf(userId))
            .claim("usr", username)
            .claim("roles", roles)
            .claim("pv", permissionVersion)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
            .signWith(key)
            .compact();
    }

    /**
     * Parses and validates the token. Returns claims on success.
     * Throws {@link JwtException} (unchecked) on any failure.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getAccessMinutes() { return accessMinutes; }
}

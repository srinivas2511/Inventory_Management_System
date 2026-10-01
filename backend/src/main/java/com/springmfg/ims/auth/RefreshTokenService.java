package com.springmfg.ims.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistence rules for rotating refresh tokens (the policy decisions live in {@link AuthService}). */
@Service
class RefreshTokenService {

    /** A freshly minted token: the raw value goes to the client once; only its hash is stored. */
    record Issued(String rawToken, RefreshToken entity) {
    }

    private final RefreshTokenRepository repository;

    RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    Issued issue(Long userId, UUID familyId, Instant expiresAt, ClientInfo client, Instant now) {
        String raw = TokenUtil.newOpaqueToken();
        RefreshToken saved = repository.saveAndFlush(new RefreshToken(userId, TokenUtil.sha256Hex(raw), familyId,
                expiresAt, client.userAgent(), client.ip(), now));
        return new Issued(raw, saved);
    }

    /** Row-locked lookup by the raw cookie value. */
    @Transactional
    Optional<RefreshToken> findForUpdate(String raw) {
        return repository.findByTokenHash(TokenUtil.sha256Hex(raw));
    }

    /** Revokes {@code old} and issues its successor in the same family with the same absolute expiry. */
    @Transactional
    Issued rotate(RefreshToken old, ClientInfo client, Instant now) {
        Issued next = issue(old.getUserId(), old.getFamilyId(), old.getExpiresAt(), client, now);
        old.revoke(now, next.entity().getId());
        repository.save(old);
        return next;
    }

    @Transactional
    void revokeFamily(UUID familyId, Instant now) {
        repository.revokeFamily(familyId, now);
    }

    @Transactional
    void revokeAllForUser(Long userId, Instant now) {
        repository.revokeAllForUser(userId, now);
    }
}

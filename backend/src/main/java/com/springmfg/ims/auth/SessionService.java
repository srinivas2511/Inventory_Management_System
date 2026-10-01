package com.springmfg.ims.auth;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends sessions on behalf of other modules (user deactivation, admin password reset). */
@Service
public class SessionService {

    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    SessionService(RefreshTokenService refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /** Revokes every refresh token of the user: all their devices must sign in again once access tokens lapse. */
    @Transactional
    public void revokeAllSessions(long userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }
}

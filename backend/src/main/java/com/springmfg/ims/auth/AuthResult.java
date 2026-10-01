package com.springmfg.ims.auth;

import java.time.Duration;

/** Outcome of an operation that may open a session: the JSON body plus the refresh token for the cookie. */
record AuthResult(AuthDtos.LoginResponse body, String refreshToken, Duration refreshMaxAge) {

    static AuthResult changeRequired() {
        return new AuthResult(AuthDtos.LoginResponse.changeRequired(), null, null);
    }

    boolean hasSession() {
        return refreshToken != null;
    }
}

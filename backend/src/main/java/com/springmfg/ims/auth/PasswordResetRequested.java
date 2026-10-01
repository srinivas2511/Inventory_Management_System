package com.springmfg.ims.auth;

/** Carries the raw reset token to the e-mail sender after the transaction commits. Never log or persist it. */
public record PasswordResetRequested(String email, String fullName, String rawToken) {

    @Override
    public String toString() {
        return "PasswordResetRequested[email=" + email + "]";
    }
}

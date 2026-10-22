package com.mfg.ims.iam.dto;

/**
 * {@code temporaryPassword} is present only when the server generated it, and only in this response: it is stored
 * hashed, never in plaintext, and not logged.
 */
public record AdminResetPasswordResponse(String temporaryPassword, boolean generated) {

    @Override
    public String toString() {
        return "AdminResetPasswordResponse[temporaryPassword=" + (temporaryPassword == null ? "null" : "***")
                + ", generated=" + generated + "]";
    }
}

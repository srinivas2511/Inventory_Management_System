package com.springmfg.ims.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code temporaryPassword} is optional: leave it out and the server generates a strong one-time password and
 * returns it once. The reason is mandatory and goes to the audit trail (the password never does).
 */
public record AdminResetPasswordRequest(
        @Size(max = 128) String temporaryPassword,
        @NotBlank @Size(max = 300) String reason) {

    @Override
    public String toString() {
        return "AdminResetPasswordRequest[temporaryPassword=" + (temporaryPassword == null ? "null" : "***")
                + ", reason=" + reason + "]";
    }
}

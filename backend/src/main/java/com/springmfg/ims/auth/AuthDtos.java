package com.springmfg.ims.auth;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/auth/*} (DESIGN.md section 6.3). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 160) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    public record UserSummary(long id, String username, String fullName, List<String> roles,
            List<String> permissions, String primaryDashboard) {
    }

    /**
     * Returned by login, refresh and change-password. When {@code mustChangePassword} is true no session was
     * opened: only that flag is present and the client must call {@code change-password}.
     */
    public record LoginResponse(String accessToken, Long expiresIn, boolean mustChangePassword, UserSummary user) {

        static LoginResponse changeRequired() {
            return new LoginResponse(null, null, true, null);
        }
    }
}

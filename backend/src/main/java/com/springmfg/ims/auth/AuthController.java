package com.springmfg.ims.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.config.ImsSecurityProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public authentication endpoints (they establish identity, so they cannot require it). The refresh token only
 * ever travels in an HttpOnly, SameSite=Strict cookie scoped to {@code /api/auth}; the access token is in the
 * response body and held in memory by the client.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    static final String REFRESH_COOKIE = "ims_refresh";
    static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final ImsSecurityProperties properties;

    AuthController(AuthService auth, ImsSecurityProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Sign in. If a password change is required, only mustChangePassword=true is returned.")
    public ResponseEntity<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest http) {
        return respond(auth.login(request, client(http)));
    }

    @PostMapping("/change-password")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Change password using the current one (forced first-login change or voluntary); signs in")
    public ResponseEntity<AuthDtos.LoginResponse> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request, HttpServletRequest http) {
        return respond(auth.changePassword(request, client(http)));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Rotate the refresh cookie and return a new access token")
    public ResponseEntity<AuthDtos.LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        return respond(auth.refresh(refreshToken, client(http)));
    }

    @PostMapping("/logout")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Revoke the refresh token family and clear the cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest http) {
        auth.logout(refreshToken, client(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", java.time.Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Request a reset e-mail. The response is the same whether or not the address is known")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request,
            HttpServletRequest http) {
        auth.forgotPassword(request.email(), client(http));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Set a new password with a single-use reset token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request,
            HttpServletRequest http) {
        auth.resetPassword(request, client(http));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthDtos.LoginResponse> respond(AuthResult result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (result.hasSession()) {
            response.header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.refreshMaxAge()).toString());
        }
        return response.body(result.body());
    }

    private ResponseCookie cookie(String value, java.time.Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static ClientInfo client(HttpServletRequest http) {
        return ClientInfo.of(http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }
}

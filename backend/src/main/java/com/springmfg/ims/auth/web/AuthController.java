package com.springmfg.ims.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.auth.dto.ChangePasswordRequest;
import com.springmfg.ims.auth.dto.ForgotPasswordRequest;
import com.springmfg.ims.auth.dto.LoginRequest;
import com.springmfg.ims.auth.dto.LoginResponse;
import com.springmfg.ims.auth.dto.ResetPasswordRequest;
import com.springmfg.ims.auth.service.AuthService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public LoginResponse login(@Valid @RequestBody LoginRequest req,
                               HttpServletRequest httpReq, HttpServletResponse httpResp) {
        return authService.login(req, httpReq, httpResp);
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public LoginResponse refresh(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        return authService.refresh(httpReq, httpResp);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        authService.logout(httpReq, httpResp);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               @AuthenticationPrincipal CachedUser principal) {
        authService.changePassword(principal.id(), req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public LoginResponse.UserInfo me(@AuthenticationPrincipal CachedUser principal) {
        return new LoginResponse.UserInfo(principal.id(), principal.username(), null,
            principal.roles(), principal.permissions());
    }
}

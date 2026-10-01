package com.springmfg.ims.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.config.ImsSecurityProperties;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.iam.UserRepository;

/**
 * Login, rotating refresh, logout, change-password and forgot/reset password (DESIGN.md section 5.1).
 * <p>
 * Failed attempts and refresh-token reuse must be <em>persisted</em> even though the call ends in an error, so
 * the methods that can fail that way declare {@code noRollbackFor = BusinessRuleException}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** Landing dashboard by role, most privileged first (DESIGN.md section 8.6). */
    private static final List<String> DASHBOARD_PRIORITY = List.of("ADMIN", "MANAGEMENT", "PRODUCTION_MANAGER",
            "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "ENGINEER", "DISPATCH", "MAINTENANCE",
            "SUPERVISOR", "STORE_OPERATOR", "OPERATOR");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final SecuritySettings settings;
    private final ImsSecurityProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    /** Verified against when the username is unknown, so response time does not reveal which usernames exist. */
    private final String decoyHash;

    AuthService(UserRepository users, PasswordEncoder encoder, PasswordService passwords, JwtService jwt,
            RefreshTokenService refreshTokens, PasswordResetTokenRepository resetTokens, SecuritySettings settings,
            ImsSecurityProperties properties, Clock clock, ApplicationEventPublisher events) {
        this.users = users;
        this.encoder = encoder;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.settings = settings;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
        this.decoyHash = encoder.encode(UUID.randomUUID().toString());
    }

    // ---------------------------------------------------------------------------------------------- login

    @Transactional(noRollbackFor = BusinessRuleException.class)
    AuthResult login(AuthDtos.LoginRequest request, ClientInfo client) {
        User user = authenticate(request.username(), request.password(), client);
        if (user.isMustChangePassword()) {
            user.clearLoginFailures();
            publish(AuthEvent.Type.PASSWORD_CHANGE_REQUIRED, user, client, null);
            return AuthResult.changeRequired();
        }
        return openSession(user, client);
    }

    /**
     * Change the password using the current one. This is the forced change on first login (no session exists
     * yet) and also the voluntary change; on success a session is opened and every older one is revoked.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    AuthResult changePassword(AuthDtos.ChangePasswordRequest request, ClientInfo client) {
        User user = authenticate(request.username(), request.currentPassword(), client);
        passwords.changePassword(user, request.newPassword());
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        publish(AuthEvent.Type.PASSWORD_CHANGED, user, client, null);
        return openSession(user, client);
    }

    private User authenticate(String rawUsername, String password, ClientInfo client) {
        String username = rawUsername.trim();
        Instant now = clock.instant();
        Optional<User> found = users.findByUsernameForUpdate(username);
        if (found.isEmpty()) {
            encoder.matches(password, decoyHash);
            publish(AuthEvent.Type.LOGIN_FAILURE, null, username, client, "unknown user");
            throw invalidCredentials();
        }
        User user = found.get();
        if (!user.isActive()) {
            encoder.matches(password, decoyHash);
            publish(AuthEvent.Type.LOGIN_FAILURE, user, client, "inactive account");
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            publish(AuthEvent.Type.LOGIN_FAILURE, user, client, "account locked");
            throw accountLocked();
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            boolean nowLocked = user.recordFailedLogin(now, settings.lockoutAttempts(), settings.lockoutDuration());
            publish(AuthEvent.Type.LOGIN_FAILURE, user, client, "wrong password");
            if (nowLocked) {
                publish(AuthEvent.Type.ACCOUNT_LOCKED, user, client,
                        "locked for " + settings.lockoutDuration().toMinutes() + " minutes");
                throw accountLocked();
            }
            throw invalidCredentials();
        }
        return user;
    }

    private AuthResult openSession(User user, ClientInfo client) {
        Instant now = clock.instant();
        user.recordSuccessfulLogin(now);
        Instant refreshExpiry = now.plus(properties.refreshTtl());
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.getId(), UUID.randomUUID(), refreshExpiry,
                client, now);
        publish(AuthEvent.Type.LOGIN_SUCCESS, user, client, null);
        return sessionResult(user, refresh.rawToken(), Duration.between(now, refreshExpiry));
    }

    // ---------------------------------------------------------------------------------------------- refresh

    /**
     * Rotates the refresh token. Presenting an already-revoked token means it was copied, so the whole family
     * (every token descended from that login) is revoked and the user must sign in again.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    AuthResult refresh(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) {
            throw notAuthenticated();
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findForUpdate(rawToken).orElseThrow(AuthService::notAuthenticated);
        Optional<User> owner = users.findById(token.getUserId());
        if (token.isRevoked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            events.publishEvent(new AuthEvent(AuthEvent.Type.TOKEN_REUSE_DETECTED, token.getUserId(),
                    owner.map(User::getUsername).orElse(null), client.ip(), "revoked refresh token presented again"));
            log.warn("Refresh token reuse detected for user id {}; token family revoked", token.getUserId());
            throw notAuthenticated();
        }
        if (token.isExpired(now)) {
            throw notAuthenticated();
        }
        if (owner.isEmpty() || !owner.get().isActive() || owner.get().isMustChangePassword()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw notAuthenticated();
        }
        User user = owner.get();
        RefreshTokenService.Issued next = refreshTokens.rotate(token, client, now);
        return sessionResult(user, next.rawToken(), Duration.between(now, token.getExpiresAt()));
    }

    // ---------------------------------------------------------------------------------------------- logout

    @Transactional
    void logout(String rawToken, ClientInfo client) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findForUpdate(rawToken).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            users.findById(token.getUserId()).ifPresent(u -> publish(AuthEvent.Type.LOGOUT, u, client, null));
        });
    }

    // ---------------------------------------------------------------------------------------------- password reset

    /** Always looks the same to the caller, whether or not the address belongs to an active account. */
    @Transactional
    void forgotPassword(String email, ClientInfo client) {
        users.findByEmailIgnoreCase(email.trim()).filter(User::isActive).ifPresent(user -> {
            Instant now = clock.instant();
            resetTokens.invalidateOpenTokens(user.getId(), now);
            String raw = TokenUtil.newOpaqueToken();
            resetTokens.save(new PasswordResetToken(user.getId(), TokenUtil.sha256Hex(raw),
                    now.plus(properties.resetTokenTtl())));
            publish(AuthEvent.Type.PASSWORD_RESET_REQUESTED, user, client, null);
            events.publishEvent(new PasswordResetRequested(user.getEmail(), user.getFullName(), raw));
        });
    }

    @Transactional
    void resetPassword(AuthDtos.ResetPasswordRequest request, ClientInfo client) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(TokenUtil.sha256Hex(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ValidationFailedException("token",
                        "This reset link is invalid or has expired. Request a new one."));
        User user = users.findById(token.getUserId()).filter(User::isActive)
                .orElseThrow(() -> new ValidationFailedException("token",
                        "This reset link is invalid or has expired. Request a new one."));
        passwords.changePassword(user, request.newPassword());
        token.markUsed(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        publish(AuthEvent.Type.PASSWORD_RESET_COMPLETED, user, client, null);
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private AuthResult sessionResult(User user, String rawRefreshToken, Duration refreshMaxAge) {
        JwtService.IssuedToken access = jwt.issue(user);
        List<String> roles = user.roleCodes().stream().sorted().toList();
        AuthDtos.UserSummary summary = new AuthDtos.UserSummary(user.getId(), user.getUsername(), user.getFullName(),
                roles, user.permissionCodes().stream().sorted().toList(), primaryDashboard(roles));
        long expiresIn = Duration.between(clock.instant(), access.expiresAt()).toSeconds();
        return new AuthResult(new AuthDtos.LoginResponse(access.value(), expiresIn, false, summary), rawRefreshToken,
                refreshMaxAge);
    }

    static String primaryDashboard(List<String> roles) {
        return DASHBOARD_PRIORITY.stream().filter(roles::contains).findFirst()
                .orElse(roles.isEmpty() ? "NONE" : roles.get(0));
    }

    private void publish(AuthEvent.Type type, User user, ClientInfo client, String detail) {
        publish(type, user, user.getUsername(), client, detail);
    }

    private void publish(AuthEvent.Type type, User user, String username, ClientInfo client, String detail) {
        events.publishEvent(new AuthEvent(type, user == null ? null : user.getId(), username, client.ip(), detail));
        log.info("auth event {} user={} ip={}{}", type, sanitize(username), client.ip(),
                detail == null ? "" : " (" + detail + ")");
    }

    /** Strips control characters so a crafted username cannot forge log lines. */
    private static String sanitize(String value) {
        return value == null ? null : value.replaceAll("[\\p{Cntrl}]", "_");
    }

    private static BusinessRuleException invalidCredentials() {
        return new BusinessRuleException(ErrorCode.UNAUTHENTICATED, "Invalid username or password.");
    }

    private static BusinessRuleException notAuthenticated() {
        return new BusinessRuleException(ErrorCode.UNAUTHENTICATED, "Your session is no longer valid. Sign in again.");
    }

    private static BusinessRuleException accountLocked() {
        return new BusinessRuleException(ErrorCode.ACCOUNT_LOCKED,
                "This account is temporarily locked after too many failed sign-in attempts. Try again later.");
    }
}

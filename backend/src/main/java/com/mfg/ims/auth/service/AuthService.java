package com.mfg.ims.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.auth.dto.ChangePasswordRequest;
import com.mfg.ims.auth.dto.LoginRequest;
import com.mfg.ims.auth.dto.LoginResponse;
import com.mfg.ims.auth.dto.LoginResponse.UserInfo;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.iam.domain.PasswordHistory;
import com.mfg.ims.iam.domain.PasswordResetToken;
import com.mfg.ims.iam.domain.RefreshToken;
import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.PasswordHistoryRepository;
import com.mfg.ims.iam.repository.PasswordResetTokenRepository;
import com.mfg.ims.iam.repository.RefreshTokenRepository;
import com.mfg.ims.iam.repository.UserRepository;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class AuthService {

    static final String REFRESH_COOKIE = "refresh_token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final PasswordHistoryRepository passwordHistoryRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SystemSettingService settings;
    private final UserPermissionCache permissionCache;
    private final AuditService auditService;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final long refreshDays;
    private final long reuseGraceSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepo,
            PasswordResetTokenRepository resetTokenRepo,
            PasswordHistoryRepository passwordHistoryRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SystemSettingService settings,
            UserPermissionCache permissionCache,
            AuditService auditService,
            JavaMailSender mailSender,
            @Value("${ims.frontend-url:http://localhost:4200}") String frontendUrl,
            @Value("${ims.security.jwt.refresh-token-days:7}") long refreshDays,
            @Value("${ims.security.jwt.refresh-reuse-grace-seconds:10}") long reuseGraceSeconds) {
        this.userRepository = userRepository;
        this.refreshTokenRepo = refreshTokenRepo;
        this.resetTokenRepo = resetTokenRepo;
        this.passwordHistoryRepo = passwordHistoryRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.settings = settings;
        this.permissionCache = permissionCache;
        this.auditService = auditService;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
        this.refreshDays = refreshDays;
        this.reuseGraceSeconds = reuseGraceSeconds;
    }

    /**
     * A token that was rotated a moment ago and whose successor is still valid is a concurrent request, not theft:
     * two tabs, or a fast Back navigation, send the same cookie before the first response has replaced it in the
     * browser (D-20). Logout, password changes and administrator revocation leave no successor, so they never qualify.
     */
    private boolean withinReuseGrace(RefreshToken rt) {
        return reuseGraceSeconds > 0
                && rt.getReplacedBy() != null
                && rt.getRevokedAt() != null
                && rt.getRevokedAt().plusSeconds(reuseGraceSeconds).isAfter(Instant.now())
                && refreshTokenRepo.findById(rt.getReplacedBy()).map(RefreshToken::isValid).orElse(false);
    }

    /**
     * {@code noRollbackFor}: the failed-attempt counter and the lockout are written and then the failure is reported
     * by an exception. With the default rollback they were never persisted, so lockout could not happen.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public LoginResponse login(LoginRequest req, HttpServletRequest httpReq, HttpServletResponse httpResp) {
        User user = userRepository.findByUsername(req.username())
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password"));

        int maxAttempts = settings.getInt("auth.lockout.max_attempts", 5);
        int lockoutMinutes = settings.getInt("auth.lockout.duration_minutes", 15);

        if (user.isLocked()) {
            auditService.record("LOGIN_LOCKED", "USER", String.valueOf(user.getId()),
                null, null, null);
            throw new BusinessRuleException(ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked");
        }

        if (!user.isActive()) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= maxAttempts) {
                user.setLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
            auditService.record("LOGIN_FAILURE", "USER", String.valueOf(user.getId()),
                null, null, null);
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        // Successful login
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getUsername(), user.getRoleCodes(), user.getPermissionVersion());

        String rawRefresh = generateSecureToken();
        String refreshHash = sha256(rawRefresh);
        RefreshToken rt = new RefreshToken(user, refreshHash, UUID.randomUUID(),
            Instant.now().plus(refreshDays, ChronoUnit.DAYS),
            httpReq.getHeader("User-Agent"), extractIp(httpReq));
        refreshTokenRepo.save(rt);
        setRefreshCookie(httpResp, rawRefresh);

        auditService.record("LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), null, null, null);

        return new LoginResponse(
            accessToken, jwtService.getAccessMinutes() * 60L, user.isMustChangePassword(),
            new UserInfo(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoleCodes(), user.getPermissionCodes(), plantScoped(user.getId()))
        );
    }

    /** {@code noRollbackFor}: reuse detection revokes the token family and then throws; that revocation must stick. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public LoginResponse refresh(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String raw = extractRefreshCookie(httpReq);
        if (raw == null) {
            throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "No refresh token");
        }

        String hash = sha256(raw);
        RefreshToken rt = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));

        boolean concurrentRotation = false;
        if (rt.isRevoked()) {
            concurrentRotation = withinReuseGrace(rt);
            if (!concurrentRotation) {
                // Reuse of revoked token — revoke entire family
                refreshTokenRepo.findByFamilyId(rt.getFamilyId())
                    .forEach(t -> { t.revoke(); refreshTokenRepo.save(t); });
                clearRefreshCookie(httpResp);
                throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Token reuse detected");
            }
        }

        if (rt.isExpired()) {
            rt.revoke();
            refreshTokenRepo.save(rt);
            clearRefreshCookie(httpResp);
            throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Refresh token expired");
        }

        User user = rt.getUser();
        if (!user.isActive()) {
            rt.revoke();
            refreshTokenRepo.save(rt);
            clearRefreshCookie(httpResp);
            throw new BusinessRuleException(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
        }

        // Rotate: revoke old, issue new. A token revoked a moment ago by a concurrent refresh keeps its original
        // successor and its original revocation time, so the grace window never extends.
        if (!concurrentRotation) {
            rt.revoke();
        }
        String newRaw = generateSecureToken();
        String newHash = sha256(newRaw);
        RefreshToken newRt = new RefreshToken(user, newHash, rt.getFamilyId(),
            Instant.now().plus(refreshDays, ChronoUnit.DAYS),
            httpReq.getHeader("User-Agent"), extractIp(httpReq));
        refreshTokenRepo.save(newRt);
        if (!concurrentRotation) {
            rt.setReplacedBy(newRt.getId());
        }
        refreshTokenRepo.save(rt);
        setRefreshCookie(httpResp, newRaw);

        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getUsername(), user.getRoleCodes(), user.getPermissionVersion());

        return new LoginResponse(
            accessToken, jwtService.getAccessMinutes() * 60L, user.isMustChangePassword(),
            new UserInfo(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoleCodes(), user.getPermissionCodes(), plantScoped(user.getId()))
        );
    }

    /** Whether the user is limited to some plants: the screens that cover all plants are hidden from them. */
    private boolean plantScoped(Long userId) {
        return !permissionCache.load(userId).plantIds().isEmpty();
    }

    @Transactional
    public void logout(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String raw = extractRefreshCookie(httpReq);
        if (raw != null) {
            refreshTokenRepo.findByTokenHash(sha256(raw)).ifPresent(rt -> {
                rt.revoke();
                refreshTokenRepo.save(rt);
            });
        }
        clearRefreshCookie(httpResp);
    }

    @Transactional
    public void forgotPassword(String email) {
        // Generic response — do not reveal whether email exists
        userRepository.findByEmail(email).ifPresent(user -> {
            String raw = generateSecureToken();
            PasswordResetToken token = new PasswordResetToken(user, sha256(raw),
                Instant.now().plusSeconds(3600)); // 1 hour
            resetTokenRepo.save(token);

            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(user.getEmail());
            msg.setSubject("IMS — Password Reset");
            msg.setText("Click the link to reset your password (expires in 1 hour):\n\n"
                + frontendUrl + "/reset-password?token=" + raw);
            mailSender.send(msg);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = resetTokenRepo.findByTokenHash(sha256(rawToken))
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Invalid or expired token"));

        if (!token.isValid()) {
            throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Token has expired or already been used");
        }

        User user = token.getUser();
        validatePasswordPolicy(newPassword, user);
        applyNewPassword(user, newPassword);
        refreshTokenRepo.revokeAllForUser(user.getId(), Instant.now());
        token.markUsed();
        resetTokenRepo.save(token);
        auditService.record("PASSWORD_RESET", "USER", String.valueOf(user.getId()), null, null, null);
    }

    /**
     * A wrong current password is a 400 on the form, never a 401: the caller's token is fine, and a 401 would make
     * the browser try a refresh and sign the user out over a typo. Every other session of the user ends: their
     * refresh tokens are revoked and their access tokens stop validating (permission version bump); the session
     * that changed the password keeps its own refresh token.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req, HttpServletRequest httpReq) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(ErrorCode.CURRENT_PASSWORD_INCORRECT, "Current password is incorrect");
        }
        validatePasswordPolicy(req.newPassword(), user);
        applyNewPassword(user, req.newPassword());
        endOtherSessions(user, httpReq);
        auditService.record("PASSWORD_CHANGE", "USER", String.valueOf(userId), null, null, null);
    }

    private void endOtherSessions(User user, HttpServletRequest httpReq) {
        Instant now = Instant.now();
        String raw = extractRefreshCookie(httpReq);
        UUID currentFamily = raw == null ? null : refreshTokenRepo.findByTokenHash(sha256(raw))
            .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())
            .map(RefreshToken::getFamilyId)
            .orElse(null);
        if (currentFamily == null) {
            refreshTokenRepo.revokeAllForUser(user.getId(), now);
        } else {
            refreshTokenRepo.revokeAllForUserExceptFamily(user.getId(), currentFamily, now);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Complexity rules only (length, character classes, no username); history is checked per existing user. */
    public void validateComplexity(String password, String username) {
        int minLen = settings.getInt("auth.password.min_length", 10);
        if (password.length() < minLen) {
            throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must be at least " + minLen + " characters");
        }
        if (!password.matches(".*[A-Z].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain an uppercase letter");
        if (!password.matches(".*[a-z].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a lowercase letter");
        if (!password.matches(".*\\d.*"))   throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a digit");
        if (!password.matches(".*[^A-Za-z0-9].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a special character");
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must not contain your username");
        }
    }

    private void validatePasswordPolicy(String password, User user) {
        validateComplexity(password, user.getUsername());

        int historyCount = settings.getInt("auth.password.history_count", 5);
        passwordHistoryRepo.findByUserIdOrderByChangedAtDesc(user.getId(), PageRequest.of(0, historyCount))
            .forEach(h -> {
                if (passwordEncoder.matches(password, h.getPasswordHash())) {
                    throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY,
                        "Password cannot be the same as your last " + historyCount + " passwords");
                }
            });
    }

    /**
     * An administrator's reset: the password becomes a temporary one the user must change at first sign-in. Every
     * session of the user ends (refresh tokens revoked, permission version bumped so open access tokens stop
     * validating), a lockout is cleared, and the permission cache is evicted after commit so the forced-change flag
     * is enforced at once.
     */
    public void setTemporaryPassword(User user, String temporaryPassword) {
        validatePasswordPolicy(temporaryPassword, user);
        passwordHistoryRepo.save(new PasswordHistory(user, user.getPasswordHash()));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setPermissionVersion(user.getPermissionVersion() + 1);
        userRepository.save(user);
        refreshTokenRepo.revokeAllForUser(user.getId(), Instant.now());
        permissionCache.evictAfterCommit(user.getId());
    }

    /** A random password that satisfies the policy; ambiguous characters (0/O, 1/l/I) are left out so it can be read out. */
    public String generateTemporaryPassword(String username) {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "!@#$%^&*-_+=?";
        String all = upper + lower + digits + special;
        int length = Math.max(16, settings.getInt("auth.password.min_length", 10));
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 20; attempt++) {
            java.util.List<Character> chars = new java.util.ArrayList<>();
            chars.add(upper.charAt(random.nextInt(upper.length())));
            chars.add(lower.charAt(random.nextInt(lower.length())));
            chars.add(digits.charAt(random.nextInt(digits.length())));
            chars.add(special.charAt(random.nextInt(special.length())));
            while (chars.size() < length) {
                chars.add(all.charAt(random.nextInt(all.length())));
            }
            java.util.Collections.shuffle(chars, random);
            StringBuilder sb = new StringBuilder(length);
            chars.forEach(sb::append);
            String candidate = sb.toString();
            try {
                validateComplexity(candidate, username);
                return candidate;
            } catch (BusinessRuleException tryAgain) {
                // e.g. the random text happened to contain the username; draw again
            }
        }
        throw new IllegalStateException("Could not generate a temporary password that meets the policy");
    }

    private void applyNewPassword(User user, String newPassword) {
        passwordHistoryRepo.save(new PasswordHistory(user, user.getPasswordHash()));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setPermissionVersion(user.getPermissionVersion() + 1);
        userRepository.save(user);
        permissionCache.evictAfterCommit(user.getId());
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set true in production behind TLS
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (refreshDays * 86400));
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (REFRESH_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}

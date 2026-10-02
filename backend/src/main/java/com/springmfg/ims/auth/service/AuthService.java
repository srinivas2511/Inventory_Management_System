package com.springmfg.ims.auth.service;

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

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.dto.ChangePasswordRequest;
import com.springmfg.ims.auth.dto.LoginRequest;
import com.springmfg.ims.auth.dto.LoginResponse;
import com.springmfg.ims.auth.dto.LoginResponse.UserInfo;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.iam.domain.PasswordHistory;
import com.springmfg.ims.iam.domain.PasswordResetToken;
import com.springmfg.ims.iam.domain.RefreshToken;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.PasswordHistoryRepository;
import com.springmfg.ims.iam.repository.PasswordResetTokenRepository;
import com.springmfg.ims.iam.repository.RefreshTokenRepository;
import com.springmfg.ims.iam.repository.UserRepository;

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
            @Value("${ims.security.jwt.refresh-token-days:7}") long refreshDays) {
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
    }

    @Transactional
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
                user.getRoleCodes(), user.getPermissionCodes())
        );
    }

    @Transactional
    public LoginResponse refresh(HttpServletRequest httpReq, HttpServletResponse httpResp) {
        String raw = extractRefreshCookie(httpReq);
        if (raw == null) {
            throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "No refresh token");
        }

        String hash = sha256(raw);
        RefreshToken rt = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Invalid refresh token"));

        if (rt.isRevoked()) {
            // Reuse of revoked token — revoke entire family
            refreshTokenRepo.findByFamilyId(rt.getFamilyId())
                .forEach(t -> { t.revoke(); refreshTokenRepo.save(t); });
            clearRefreshCookie(httpResp);
            throw new BusinessRuleException(ErrorCode.INVALID_TOKEN, "Token reuse detected");
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

        // Rotate: revoke old, issue new
        rt.revoke();
        String newRaw = generateSecureToken();
        String newHash = sha256(newRaw);
        RefreshToken newRt = new RefreshToken(user, newHash, rt.getFamilyId(),
            Instant.now().plus(refreshDays, ChronoUnit.DAYS),
            httpReq.getHeader("User-Agent"), extractIp(httpReq));
        refreshTokenRepo.save(newRt);
        rt.setReplacedBy(newRt.getId());
        refreshTokenRepo.save(rt);
        setRefreshCookie(httpResp, newRaw);

        String accessToken = jwtService.generateAccessToken(
            user.getId(), user.getUsername(), user.getRoleCodes(), user.getPermissionVersion());

        return new LoginResponse(
            accessToken, jwtService.getAccessMinutes() * 60L, user.isMustChangePassword(),
            new UserInfo(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoleCodes(), user.getPermissionCodes())
        );
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
        token.markUsed();
        resetTokenRepo.save(token);
        auditService.record("PASSWORD_RESET", "USER", String.valueOf(user.getId()), null, null, null);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        validatePasswordPolicy(req.newPassword(), user);
        applyNewPassword(user, req.newPassword());
        auditService.record("PASSWORD_CHANGE", "USER", String.valueOf(userId), null, null, null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void validatePasswordPolicy(String password, User user) {
        int minLen = settings.getInt("auth.password.min_length", 10);
        if (password.length() < minLen) {
            throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must be at least " + minLen + " characters");
        }
        if (!password.matches(".*[A-Z].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain an uppercase letter");
        if (!password.matches(".*[a-z].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a lowercase letter");
        if (!password.matches(".*\\d.*"))   throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a digit");
        if (!password.matches(".*[^A-Za-z0-9].*")) throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must contain a special character");
        if (user.getUsername() != null && password.toLowerCase().contains(user.getUsername().toLowerCase())) {
            throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY, "Password must not contain your username");
        }

        int historyCount = settings.getInt("auth.password.history_count", 5);
        passwordHistoryRepo.findByUserIdOrderByChangedAtDesc(user.getId(), PageRequest.of(0, historyCount))
            .forEach(h -> {
                if (passwordEncoder.matches(password, h.getPasswordHash())) {
                    throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY,
                        "Password cannot be the same as your last " + historyCount + " passwords");
                }
            });
    }

    private void applyNewPassword(User user, String newPassword) {
        passwordHistoryRepo.save(new PasswordHistory(user, user.getPasswordHash()));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        userRepository.save(user);
        permissionCache.evict(user.getId());
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

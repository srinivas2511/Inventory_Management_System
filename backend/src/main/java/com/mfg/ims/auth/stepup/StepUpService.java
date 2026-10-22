package com.mfg.ims.auth.stepup;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Step-up authentication (SEC-010). When {@code security.stepup.enabled} is on, the sensitive actions call
 * {@link #requireFresh()}: the user must have proved who they are again within {@code security.stepup.minutes}.
 * Elevation is remembered per user in the database, so it holds across nodes and a restart.
 */
@Service
@Transactional
public class StepUpService {

    public record Status(boolean required, Instant validUntil) {}

    private final SystemSettingService settings;
    private final List<StepUpMethod> methods;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public StepUpService(SystemSettingService settings, List<StepUpMethod> methods, JdbcTemplate jdbc,
                         AuditService audit, Clock clock) {
        this.settings = settings;
        this.methods = methods;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Throws STEP_UP_REQUIRED when the feature is on and the user has not re-authenticated recently. */
    @Transactional(readOnly = true)
    public void requireFresh() {
        if (!enabled()) {
            return;
        }
        Instant until = validUntil(currentUserId());
        if (until == null || !until.isAfter(clock.instant())) {
            throw new BusinessRuleException(ErrorCode.STEP_UP_REQUIRED,
                    "This action needs you to confirm who you are. Enter your credential to continue.");
        }
    }

    @Transactional(readOnly = true)
    public Status status() {
        Long userId = currentUserId();
        Instant until = validUntil(userId);
        boolean valid = until != null && until.isAfter(clock.instant());
        return new Status(enabled(), valid ? until : null);
    }

    /**
     * Checks the credential and, when right, opens the elevation window. A wrong credential changes nothing.
     * Deliberately outside a transaction: the audit row of a failed attempt must commit even though the refusal is an
     * exception, and each statement here is already atomic on its own.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Status elevate(String credential) {
        Long userId = currentUserId();
        String name = settings.get("security.stepup.method", "PASSWORD");
        StepUpMethod method = methods.stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "The step-up method " + name + " is not available"));
        if (!method.verify(userId, credential)) {
            audit.record("STEP_UP_FAILED", "USER", String.valueOf(userId), null, null, null);
            throw new BusinessRuleException(ErrorCode.CURRENT_PASSWORD_INCORRECT, "The credential is not correct");
        }
        Instant until = clock.instant().plusSeconds(60L * Math.max(1, settings.getInt("security.stepup.minutes", 5)));
        jdbc.update("UPDATE ims.users SET step_up_until = ? WHERE id = ?", java.sql.Timestamp.from(until), userId);
        audit.record("STEP_UP_GRANTED", "USER", String.valueOf(userId), null, null, null);
        return new Status(enabled(), until);
    }

    private boolean enabled() {
        return settings.getBoolean("security.stepup.enabled", false);
    }

    private Instant validUntil(Long userId) {
        List<java.sql.Timestamp> rows = jdbc.queryForList("SELECT step_up_until FROM ims.users WHERE id = ?",
                java.sql.Timestamp.class, userId);
        return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toInstant();
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
            return user.id();
        }
        throw new BusinessRuleException(ErrorCode.UNAUTHENTICATED, "Sign in to continue");
    }
}

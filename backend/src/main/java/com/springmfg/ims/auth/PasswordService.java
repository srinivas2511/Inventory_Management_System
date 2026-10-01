package com.springmfg.ims.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.springmfg.ims.common.exception.Problems;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.iam.User;

/**
 * Password policy and history (DESIGN.md section 5.1): at least the configured length, upper and lower case, a
 * digit and a symbol, not containing the username, and not equal to the current or any of the last 5 passwords.
 */
@Service
public class PasswordService {

    static final int HISTORY_DEPTH = 5;
    /** BCrypt ignores everything beyond 72 bytes; refuse such passwords rather than silently truncate them. */
    static final int MAX_BYTES = 72;
    static final String FIELD = "newPassword";

    private final PasswordEncoder encoder;
    private final SecuritySettings settings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    PasswordService(PasswordEncoder encoder, SecuritySettings settings, JdbcTemplate jdbc, Clock clock) {
        this.encoder = encoder;
        this.settings = settings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Pure rule check (no history). Returns every violation so the user can fix them in one go. */
    List<String> violations(String password, String username) {
        List<String> problems = new ArrayList<>();
        int min = settings.minPasswordLength();
        if (password.length() < min) {
            problems.add("Must be at least " + min + " characters long.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("Must be at most " + MAX_BYTES + " bytes long.");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("Must contain an upper-case letter.");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("Must contain a lower-case letter.");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Must contain a digit.");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("Must contain a symbol.");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            problems.add("Must not contain the username.");
        }
        return problems;
    }

    /** Throws {@link ValidationFailedException} (HTTP 400, field {@code newPassword}) if the password is not acceptable. */
    public void validate(User user, String password) {
        List<String> problems = violations(password, user.getUsername());
        if (problems.isEmpty() && wasUsedRecently(user, password)) {
            problems.add("Must differ from your current and previous " + (HISTORY_DEPTH - 1) + " passwords.");
        }
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(
                    problems.stream().map(m -> new Problems.FieldError(FIELD, m)).toList());
        }
    }

    /** Validates, hashes and stores a new password the user chose themselves (clears the forced-change flag). */
    public void changePassword(User user, String newPassword) {
        validate(user, newPassword);
        Instant now = clock.instant();
        recordHistory(user.getId(), user.getPasswordHash(), user.getPasswordChangedAt());
        String hash = encoder.encode(newPassword);
        user.changePassword(hash, now);
        recordHistory(user.getId(), hash, now);
    }

    /**
     * Stores a password set by someone else (new account or admin reset): validated like any other, but the user
     * must choose their own at next login. {@code field} names the request field in validation errors.
     */
    public void setTemporaryPassword(User user, String temporaryPassword, String field) {
        List<String> problems = violations(temporaryPassword, user.getUsername());
        if (!problems.isEmpty()) {
            throw new ValidationFailedException(
                    problems.stream().map(m -> new Problems.FieldError(field, m)).toList());
        }
        Instant now = clock.instant();
        String hash = encoder.encode(temporaryPassword);
        user.requirePasswordChange(hash, now);
    }

    /** Call once the user has an id so the first password enters the history. */
    public void recordCurrentPassword(User user) {
        recordHistory(user.getId(), user.getPasswordHash(), clock.instant());
    }

    private boolean wasUsedRecently(User user, String password) {
        if (user.getPasswordHash() != null && encoder.matches(password, user.getPasswordHash())) {
            return true;
        }
        List<String> hashes = jdbc.queryForList(
                "SELECT password_hash FROM password_history WHERE user_id = ? ORDER BY changed_at DESC LIMIT ?",
                String.class, user.getId(), HISTORY_DEPTH);
        for (String hash : hashes) {
            if (!hash.equals(user.getPasswordHash()) && encoder.matches(password, hash)) {
                return true;
            }
        }
        return false;
    }

    private void recordHistory(Long userId, String hash, Instant changedAt) {
        if (hash == null) {
            return;
        }
        Integer exists = jdbc.queryForObject(
                "SELECT count(*) FROM password_history WHERE user_id = ? AND password_hash = ?", Integer.class, userId, hash);
        if (exists == null || exists == 0) {
            jdbc.update("INSERT INTO password_history (user_id, password_hash, changed_at) VALUES (?, ?, ?)",
                    userId, hash, java.sql.Timestamp.from(changedAt != null ? changedAt : clock.instant()));
        }
    }
}

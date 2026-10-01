package com.springmfg.ims.settings;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.springmfg.ims.audit.Audited;
import com.springmfg.ims.auth.CurrentUser;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.common.tx.AfterCommit;

/**
 * Typed access to {@code system_settings} (DESIGN.md section 10.1) through a short-lived cache. A change made here
 * evicts the cache after commit, so it applies on the next read; the 60-second expiry only matters if someone edits
 * the table directly. A missing or unreadable row falls back to the {@link SettingKey#defaultValue() default}.
 * Every change is audited with its old and new value and an optional reason.
 */
@Service
public class SystemSettingService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final Cache<String, String> cache = Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(1_000).build();

    SystemSettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------------------------------------- typed reads

    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(raw(key, SettingValueType.BOOLEAN));
    }

    public int getInt(SettingKey key) {
        return Integer.parseInt(raw(key, SettingValueType.INTEGER));
    }

    public BigDecimal getDecimal(SettingKey key) {
        return new BigDecimal(raw(key, SettingValueType.DECIMAL));
    }

    public String getString(SettingKey key) {
        return raw(key, SettingValueType.STRING);
    }

    private String raw(SettingKey key, SettingValueType expected) {
        if (key.type() != expected) {
            throw new IllegalArgumentException("Setting " + key.key() + " is " + key.type() + ", not " + expected);
        }
        String stored = cache.get(key.key(), k -> load(key));
        try {
            key.normalise(stored); // a hand-edited bad value must not take the application down
            return stored;
        } catch (IllegalArgumentException e) {
            return key.defaultValue();
        }
    }

    private String load(SettingKey key) {
        try {
            List<String> values = jdbc.queryForList("SELECT value FROM system_settings WHERE key = ?", String.class, key.key());
            return values.isEmpty() ? key.defaultValue() : values.get(0);
        } catch (DataAccessException e) {
            return key.defaultValue();
        }
    }

    // ---------------------------------------------------------------------------------------------- administration

    /** Every stored setting, known ones first-class; sorted by key. */
    @Transactional(readOnly = true)
    public List<SettingView> list() {
        List<SettingView> views = new ArrayList<>();
        jdbc.query("SELECT key, value, value_type, description, updated_at, updated_by FROM system_settings ORDER BY key", rs -> {
            Timestamp updated = rs.getTimestamp("updated_at");
            views.add(view(rs.getString("key"), rs.getString("value"), rs.getString("value_type"),
                    rs.getString("description"), updated == null ? null : updated.toInstant(), (Long) rs.getObject("updated_by")));
        });
        return views;
    }

    @Transactional(readOnly = true)
    public SettingView get(String key) {
        return list().stream().filter(v -> v.key().equals(key)).findFirst().orElseThrow(() -> new NotFoundException("Setting", key));
    }

    /**
     * Changes a setting. Unknown keys are refused (no code would read them); the value is validated against the
     * key's type and limits.
     */
    @Transactional
    @Audited(action = "SETTING_CHANGED", entity = "SystemSetting")
    public SettingChange update(String key, String value, String reason) {
        SettingKey known = SettingKey.find(key).orElseThrow(() -> new NotFoundException("Setting", key));
        String canonical;
        try {
            canonical = known.normalise(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationFailedException("value", e.getMessage());
        }
        String old = jdbc.queryForObject("SELECT value FROM system_settings WHERE key = ?", String.class, key);
        jdbc.update("UPDATE system_settings SET value = ?, updated_at = now(), updated_by = ? WHERE key = ?",
                canonical, CurrentUser.id().orElse(null), key);
        AfterCommit.run(() -> cache.invalidate(key));
        return new SettingChange(get(key), old, reason);
    }

    private SettingView view(String key, String value, String type, String description, java.time.Instant updatedAt, Long updatedBy) {
        Optional<SettingKey> known = SettingKey.find(key);
        return new SettingView(key, value, SettingValueType.valueOf(type), description,
                known.map(SettingKey::defaultValue).orElse(null), known.map(SettingKey::allowedValues).orElse(List.of()),
                known.flatMap(SettingKey::min).map(BigDecimal::toPlainString).orElse(null),
                known.flatMap(SettingKey::max).map(BigDecimal::toPlainString).orElse(null), known.isPresent(), updatedAt, updatedBy);
    }

    /** Test and tooling hook: drop everything cached. */
    public void evictAll() {
        cache.invalidateAll();
    }

    /** Snapshot of the raw cache keys currently held (diagnostics). */
    Map<String, String> cached() {
        return new HashMap<>(cache.asMap());
    }
}

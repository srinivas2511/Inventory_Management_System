package com.springmfg.ims.settings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Every runtime setting the application knows (DESIGN.md section 10.1) with its type, default and permitted values.
 * The defaults here equal the seeded rows, so a missing row behaves like the seed. Code asks for a
 * {@code SettingKey}, never for a raw string, so a typo cannot compile.
 */
public enum SettingKey {

    INVENTORY_ALLOW_NEGATIVE_STOCK("inventory.allow_negative_stock", SettingValueType.BOOLEAN, "false",
            "Allow stock to go negative (Admin only; business rule 1)", null, null),
    INVENTORY_ADJUSTMENT_ADMIN_THRESHOLD("inventory.adjustment.admin_threshold", SettingValueType.DECIMAL, "10000.00",
            "Variance value (INR) above which a stock adjustment needs Admin approval", "0", null),
    INVENTORY_COSTING_METHOD("inventory.costing.method", SettingValueType.STRING, "MOVING_AVERAGE",
            "Inventory costing method", null, null, "MOVING_AVERAGE"),
    INVENTORY_AGING_DAYS("inventory.aging_days", SettingValueType.INTEGER, "180",
            "Days in stock before an aging alert", "1", null),
    INVENTORY_EXPIRY_WARNING_DAYS("inventory.expiry_warning_days", SettingValueType.INTEGER, "30",
            "Days before expiry to raise an alert", "1", null),
    PURCHASE_OVER_RECEIPT_PCT("purchase.over_receipt_pct", SettingValueType.DECIMAL, "5",
            "Over-receipt tolerance on purchase orders (%)", "0", "100"),
    PRODUCTION_RELEASE_REQUIRE_MATERIAL("production.release.require_material", SettingValueType.BOOLEAN, "false",
            "Block production release when material is short", null, null),
    PRODUCTION_ISSUE_TOLERANCE_PCT("production.issue.tolerance_pct", SettingValueType.DECIMAL, "5",
            "Over-issue tolerance against the BOM (%)", "0", "100"),
    PRODUCTION_AUTO_BACKFLUSH("production.auto_backflush", SettingValueType.BOOLEAN, "true",
            "Create MATERIAL_CONSUMPTION automatically from production output", null, null),
    PRODUCTION_MONTHLY_TARGET("production.monthly_target", SettingValueType.INTEGER, "0",
            "Monthly production target shown on the dashboard", "0", null),
    QUALITY_PENDING_HOURS("quality.pending_hours", SettingValueType.INTEGER, "24",
            "Hours a pending inspection may wait before an alert", "1", null),
    QUALITY_TRACE_GRANULARITY("quality.trace_granularity", SettingValueType.STRING, "ORDER",
            "Traceability granularity: ORDER or BATCH", null, null, "ORDER", "BATCH"),
    SALES_RESERVATION_MAX_DAYS("sales.reservation.max_days", SettingValueType.INTEGER, "14",
            "Days before a stale FG reservation is released automatically", "1", null),
    SECURITY_PASSWORD_MIN_LENGTH("security.password.min_length", SettingValueType.INTEGER, "12",
            "Minimum password length", "8", "72"),
    SECURITY_LOCKOUT_ATTEMPTS("security.lockout.attempts", SettingValueType.INTEGER, "5",
            "Failed logins before the account is locked", "1", "100"),
    SECURITY_LOCKOUT_MINUTES("security.lockout.minutes", SettingValueType.INTEGER, "15",
            "Account lockout duration in minutes", "1", "1440");

    private final String key;
    private final SettingValueType type;
    private final String defaultValue;
    private final String description;
    private final BigDecimal min;
    private final BigDecimal max;
    private final List<String> allowed;

    SettingKey(String key, SettingValueType type, String defaultValue, String description, String min, String max,
            String... allowed) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.description = description;
        this.min = min == null ? null : new BigDecimal(min);
        this.max = max == null ? null : new BigDecimal(max);
        this.allowed = List.of(allowed);
    }

    public String key() {
        return key;
    }

    public SettingValueType type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public String description() {
        return description;
    }

    public Optional<BigDecimal> min() {
        return Optional.ofNullable(min);
    }

    public Optional<BigDecimal> max() {
        return Optional.ofNullable(max);
    }

    public List<String> allowedValues() {
        return allowed;
    }

    public static Optional<SettingKey> find(String key) {
        for (SettingKey candidate : values()) {
            if (candidate.key.equals(key)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks {@code raw} against the type and limits and returns the canonical text to store.
     *
     * @throws IllegalArgumentException with a user-readable message if the value is not acceptable
     */
    public String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("A value is required.");
        }
        String value = raw.trim();
        switch (type) {
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Must be true or false.");
                }
                return value.toLowerCase(java.util.Locale.ROOT);
            }
            case INTEGER -> {
                try {
                    return checkRange(new BigDecimal(Long.parseLong(value)), String.valueOf(Long.parseLong(value)));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Must be a whole number.");
                }
            }
            case DECIMAL -> {
                try {
                    BigDecimal number = new BigDecimal(value);
                    return checkRange(number, number.toPlainString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Must be a number.");
                }
            }
            default -> {
                if (value.length() > 500) {
                    throw new IllegalArgumentException("Must be at most 500 characters.");
                }
                if (!allowed.isEmpty() && !allowed.contains(value)) {
                    throw new IllegalArgumentException("Must be one of: " + String.join(", ", allowed) + ".");
                }
                return value;
            }
        }
    }

    private String checkRange(BigDecimal number, String canonical) {
        if (min != null && number.compareTo(min) < 0) {
            throw new IllegalArgumentException("Must be at least " + min.toPlainString() + ".");
        }
        if (max != null && number.compareTo(max) > 0) {
            throw new IllegalArgumentException("Must be at most " + max.toPlainString() + ".");
        }
        return canonical;
    }
}

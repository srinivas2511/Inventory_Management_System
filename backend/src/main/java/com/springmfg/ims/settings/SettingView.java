package com.springmfg.ims.settings;

import java.time.Instant;
import java.util.List;

/** A setting as shown to administrators. {@code known} is false for a row no code reads (read-only). */
public record SettingView(String key, String value, SettingValueType type, String description, String defaultValue,
        List<String> allowedValues, String min, String max, boolean known, Instant updatedAt, Long updatedBy) {
}

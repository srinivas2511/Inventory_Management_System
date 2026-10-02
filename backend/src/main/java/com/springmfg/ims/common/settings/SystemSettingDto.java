package com.springmfg.ims.common.settings;

import java.time.Instant;

public record SystemSettingDto(
        String key,
        String value,
        String valueType,
        String description,
        Instant updatedAt) {

    public static SystemSettingDto from(SystemSetting s) {
        return new SystemSettingDto(s.getKey(), s.getValue(), s.getValueType(),
            s.getDescription(), s.getUpdatedAt());
    }
}

package com.springmfg.ims.settings;

import java.util.Map;

import com.springmfg.ims.audit.AuditChange;
import com.springmfg.ims.audit.Auditable;

/** Result of {@link SystemSettingService#update}: the new state and what the audit log must say. */
public record SettingChange(SettingView setting, String oldValue, String reason) implements Auditable {

    @Override
    public AuditChange auditChange() {
        return new AuditChange(setting.key(), Map.of("value", oldValue), Map.of("value", setting.value()), reason);
    }
}

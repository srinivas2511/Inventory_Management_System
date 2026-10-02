package com.springmfg.ims.common.settings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;

@Service
public class SystemSettingService {

    private final SystemSettingRepository repository;

    public SystemSettingService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "systemSettings", key = "#key")
    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return repository.findById(key)
            .map(SystemSetting::getValue)
            .orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val.trim());
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String val = get(key, null);
        if (val == null) return defaultValue;
        try { return new BigDecimal(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll() {
        return repository.findAll();
    }

    @CacheEvict(value = "systemSettings", key = "#key")
    @Transactional
    public SystemSetting update(String key, String value) {
        SystemSetting setting = repository.findById(key)
            .orElseThrow(() -> new NotFoundException("Setting not found: " + key));

        validateType(setting.getValueType(), value);
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now());
        Long userId = resolveCurrentUserId();
        setting.setUpdatedBy(userId);
        repository.save(setting);
        return setting;
    }

    private void validateType(String valueType, String value) {
        try {
            switch (valueType) {
                case "INTEGER" -> Integer.parseInt(value.trim());
                case "DECIMAL" -> new BigDecimal(value.trim());
                case "BOOLEAN" -> {
                    String v = value.trim().toLowerCase();
                    if (!v.equals("true") && !v.equals("false"))
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Value must be true or false");
                }
                default -> {} // STRING — no validation
            }
        } catch (NumberFormatException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "Value '" + value + "' is not a valid " + valueType);
        }
    }

    private Long resolveCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) return u.id();
        return null;
    }
}

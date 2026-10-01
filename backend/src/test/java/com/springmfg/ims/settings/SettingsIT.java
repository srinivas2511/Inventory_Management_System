package com.springmfg.ims.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.TestUsers;

/** Task 1.7: SystemSettingService (typed, cached, audited) and {@code /api/settings}. Every change is undone. */
class SettingsIT extends AbstractIntegrationTest {

    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT");

    @Autowired
    SystemSettingService settings;
    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    private String adminToken() throws Exception {
        User admin = testUsers.create("ADMIN");
        return Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
    }

    private ResultActions putSetting(String token, String key, Object body) throws Exception {
        return mockMvc.perform(Api.bearer(Api.jsonBody(put("/api/settings/" + key), json, body), token));
    }

    private String stored(SettingKey key) {
        return jdbc.queryForObject("SELECT value FROM system_settings WHERE key = ?", String.class, key.key());
    }

    private void restore(SettingKey key) {
        jdbc.update("UPDATE system_settings SET value = ?, updated_at = NULL, updated_by = NULL WHERE key = ?", key.defaultValue(), key.key());
        settings.evictAll();
    }

    // ------------------------------------------------------------------------------------------- reads

    @Test
    void everyKnownSettingIsSeededWithItsDefaultAndTheTypeMatches() {
        for (SettingKey key : SettingKey.values()) {
            Map<String, Object> row = jdbc.queryForMap("SELECT value, value_type FROM system_settings WHERE key = ?", key.key());
            assertThat(row.get("value")).as(key.key()).isEqualTo(key.defaultValue());
            assertThat(row.get("value_type")).as(key.key()).isEqualTo(key.type().name());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM system_settings", Integer.class)).isEqualTo(SettingKey.values().length);
    }

    @Test
    void typedGettersReturnTheSeededValues() {
        assertThat(settings.getInt(SettingKey.SECURITY_PASSWORD_MIN_LENGTH)).isEqualTo(12);
        assertThat(settings.getInt(SettingKey.SECURITY_LOCKOUT_ATTEMPTS)).isEqualTo(5);
        assertThat(settings.getInt(SettingKey.SECURITY_LOCKOUT_MINUTES)).isEqualTo(15);
        assertThat(settings.getBoolean(SettingKey.INVENTORY_ALLOW_NEGATIVE_STOCK)).isFalse();
        assertThat(settings.getBoolean(SettingKey.PRODUCTION_AUTO_BACKFLUSH)).isTrue();
        assertThat(settings.getDecimal(SettingKey.INVENTORY_ADJUSTMENT_ADMIN_THRESHOLD)).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(settings.getString(SettingKey.QUALITY_TRACE_GRANULARITY)).isEqualTo("ORDER");
    }

    @Test
    void askingForTheWrongTypeIsAProgrammingError() {
        assertThatThrownBy(() -> settings.getBoolean(SettingKey.SECURITY_LOCKOUT_ATTEMPTS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.getInt(SettingKey.QUALITY_TRACE_GRANULARITY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBrokenStoredValueFallsBackToTheDefaultInsteadOfFailing() {
        SettingKey key = SettingKey.PRODUCTION_MONTHLY_TARGET;
        try {
            jdbc.update("UPDATE system_settings SET value = 'not a number' WHERE key = ?", key.key());
            settings.evictAll();
            assertThat(settings.getInt(key)).isEqualTo(0);
            jdbc.update("DELETE FROM system_settings WHERE key = ?", key.key());
            settings.evictAll();
            assertThat(settings.getInt(key)).isEqualTo(0); // a missing row behaves like the seed
        } finally {
            jdbc.update("INSERT INTO system_settings (key, value, value_type, description) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                    key.key(), key.defaultValue(), key.type().name(), key.description());
            restore(key);
        }
    }

    // ------------------------------------------------------------------------------------------- update

    @Test
    void anUpdateIsValidatedStoredAttributedAndVisibleAtOnce() throws Exception {
        SettingKey key = SettingKey.PRODUCTION_MONTHLY_TARGET;
        User admin = testUsers.create("ADMIN");
        String token = Api.login(mockMvc, json, admin.getUsername(), TestUsers.PASSWORD).accessToken();
        try {
            assertThat(settings.getInt(key)).isZero(); // warm the cache
            putSetting(token, key.key(), Map.of("value", "1500", "reason", "Q4 plan")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.key").value(key.key())).andExpect(jsonPath("$.value").value("1500"))
                    .andExpect(jsonPath("$.type").value("INTEGER")).andExpect(jsonPath("$.defaultValue").value("0"))
                    .andExpect(jsonPath("$.updatedBy").value(admin.getId())).andExpect(jsonPath("$.updatedAt").isNotEmpty())
                    .andExpect(jsonPath("$.known").value(true));

            assertThat(settings.getInt(key)).as("cache evicted after commit").isEqualTo(1500);
            assertThat(stored(key)).isEqualTo("1500");
            Map<String, Object> audit = jdbc.queryForMap("""
                    SELECT user_id, old_value ->> 'value' AS old_v, new_value ->> 'value' AS new_v, reason, entity_id
                    FROM audit_logs WHERE action = 'SETTING_CHANGED' AND entity_id = ? ORDER BY id DESC LIMIT 1""", key.key());
            assertThat(audit.get("user_id")).isEqualTo(admin.getId());
            assertThat(audit.get("old_v")).isEqualTo("0");
            assertThat(audit.get("new_v")).isEqualTo("1500");
            assertThat(audit.get("reason")).isEqualTo("Q4 plan");
        } finally {
            restore(key);
        }
    }

    @Test
    void invalidValuesAndUnknownKeysAreRejectedAndNothingChanges() throws Exception {
        String token = adminToken();
        putSetting(token, "security.lockout.attempts", Map.of("value", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value")).andExpect(jsonPath("$.fieldErrors[0].message").value("Must be at least 1."));
        putSetting(token, "security.password.min_length", Map.of("value", "4")).andExpect(status().isBadRequest());
        putSetting(token, "inventory.allow_negative_stock", Map.of("value", "maybe")).andExpect(status().isBadRequest());
        putSetting(token, "quality.trace_granularity", Map.of("value", "PRODUCT")).andExpect(status().isBadRequest());
        putSetting(token, "inventory.adjustment.admin_threshold", Map.of("value", "lots")).andExpect(status().isBadRequest());
        putSetting(token, "security.lockout.attempts", Map.of("value", "")).andExpect(status().isBadRequest());
        putSetting(token, "no.such.setting", Map.of("value", "1")).andExpect(status().isNotFound());
        assertThat(stored(SettingKey.SECURITY_LOCKOUT_ATTEMPTS)).isEqualTo("5");
        assertThat(stored(SettingKey.QUALITY_TRACE_GRANULARITY)).isEqualTo("ORDER");
    }

    @Test
    void listShowsEverySettingWithItsMetadata() throws Exception {
        String token = adminToken();
        mockMvc.perform(Api.bearer(get("/api/settings"), token)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[?(@.key == 'security.lockout.attempts')].min").value(hasItem("1")))
                .andExpect(jsonPath("$[?(@.key == 'security.lockout.attempts')].max").value(hasItem("100")))
                .andExpect(jsonPath("$[?(@.key == 'quality.trace_granularity')].allowedValues[0]").value(hasItem("ORDER")))
                .andExpect(jsonPath("$[?(@.key == 'inventory.adjustment.admin_threshold')].type").value(hasItem("DECIMAL")));
    }

    @Test
    void changingTheLockoutSettingChangesHowSignInsAreLockedWithoutARestart() throws Exception {
        SettingKey key = SettingKey.SECURITY_LOCKOUT_ATTEMPTS;
        String token = adminToken();
        User victim = testUsers.create("SALES");
        try {
            putSetting(token, key.key(), Map.of("value", "3")).andExpect(status().isOk());
            for (int i = 0; i < 2; i++) {
                loginAttempt(victim.getUsername()).andExpect(status().isUnauthorized());
            }
            loginAttempt(victim.getUsername()).andExpect(status().isLocked()); // the third failure now locks
        } finally {
            restore(key);
        }
    }

    private ResultActions loginAttempt(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "Wrong-Pass-1234!"))));
    }

    // ------------------------------------------------------------------------------------------- access

    @Test
    void onlyRolesHoldingSettingsManageMayReadOrChangeSettings() throws Exception {
        for (String role : ROLES) {
            String token = Api.login(mockMvc, json, testUsers.create(role).getUsername(), TestUsers.PASSWORD).accessToken();
            Integer holds = jdbc.queryForObject("""
                    SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
                    JOIN permissions p ON p.id = rp.permission_id WHERE r.code = ? AND p.code = 'SETTINGS_MANAGE'""", Integer.class, role);
            if (holds != null && holds > 0) {
                mockMvc.perform(Api.bearer(get("/api/settings"), token)).andExpect(status().isOk());
            } else {
                mockMvc.perform(Api.bearer(get("/api/settings"), token)).andExpect(status().isForbidden());
                putSetting(token, "production.monthly_target", Map.of("value", "999")).andExpect(status().isForbidden()); // valid payload
            }
        }
        assertThat(stored(SettingKey.PRODUCTION_MONTHLY_TARGET)).isEqualTo("0");
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    }
}

package com.springmfg.ims.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class SettingKeyTest {

    @Test
    void thereAreSixteenUniqueKeysEachWithADefaultThatPassesItsOwnValidation() {
        assertThat(SettingKey.values()).hasSize(16);
        assertThat(Arrays.stream(SettingKey.values()).map(SettingKey::key).distinct().count()).isEqualTo(16);
        for (SettingKey key : SettingKey.values()) {
            assertThat(key.normalise(key.defaultValue())).as(key.key()).isNotBlank();
            assertThat(SettingKey.find(key.key())).contains(key);
        }
        assertThat(SettingKey.find("no.such.key")).isEmpty();
    }

    @Test
    void booleansAcceptTrueAndFalseInAnyCase() {
        assertThat(SettingKey.INVENTORY_ALLOW_NEGATIVE_STOCK.normalise(" TRUE ")).isEqualTo("true");
        assertThatThrownBy(() -> SettingKey.INVENTORY_ALLOW_NEGATIVE_STOCK.normalise("yes")).hasMessageContaining("true or false");
    }

    @Test
    void integersAreCheckedForTypeAndRange() {
        assertThat(SettingKey.SECURITY_LOCKOUT_ATTEMPTS.normalise("7")).isEqualTo("7");
        assertThatThrownBy(() -> SettingKey.SECURITY_LOCKOUT_ATTEMPTS.normalise("3.5")).hasMessageContaining("whole number");
        assertThatThrownBy(() -> SettingKey.SECURITY_LOCKOUT_ATTEMPTS.normalise("0")).hasMessageContaining("at least 1");
        assertThatThrownBy(() -> SettingKey.SECURITY_LOCKOUT_ATTEMPTS.normalise("101")).hasMessageContaining("at most 100");
        assertThatThrownBy(() -> SettingKey.SECURITY_PASSWORD_MIN_LENGTH.normalise("7")).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> SettingKey.SECURITY_PASSWORD_MIN_LENGTH.normalise("73")).hasMessageContaining("at most 72");
    }

    @Test
    void decimalsKeepTheirScaleAndAreRangeChecked() {
        assertThat(SettingKey.INVENTORY_ADJUSTMENT_ADMIN_THRESHOLD.normalise("25000.50")).isEqualTo("25000.50");
        assertThatThrownBy(() -> SettingKey.INVENTORY_ADJUSTMENT_ADMIN_THRESHOLD.normalise("-1")).hasMessageContaining("at least 0");
        assertThatThrownBy(() -> SettingKey.PURCHASE_OVER_RECEIPT_PCT.normalise("100.5")).hasMessageContaining("at most 100");
        assertThatThrownBy(() -> SettingKey.PURCHASE_OVER_RECEIPT_PCT.normalise("five")).hasMessageContaining("number");
    }

    @Test
    void stringsMustBeOneOfThePermittedValues() {
        assertThat(SettingKey.QUALITY_TRACE_GRANULARITY.normalise("BATCH")).isEqualTo("BATCH");
        assertThatThrownBy(() -> SettingKey.QUALITY_TRACE_GRANULARITY.normalise("batch")).hasMessageContaining("ORDER, BATCH");
        assertThatThrownBy(() -> SettingKey.INVENTORY_COSTING_METHOD.normalise("FIFO")).hasMessageContaining("MOVING_AVERAGE");
    }

    @Test
    void blankValuesAreRefused() {
        assertThatThrownBy(() -> SettingKey.INVENTORY_AGING_DAYS.normalise("  ")).hasMessageContaining("required");
        assertThatThrownBy(() -> SettingKey.INVENTORY_AGING_DAYS.normalise(null)).hasMessageContaining("required");
    }
}

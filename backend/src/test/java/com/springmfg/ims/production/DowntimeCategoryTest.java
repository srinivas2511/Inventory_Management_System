package com.springmfg.ims.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.production.domain.DowntimeCategory;

class DowntimeCategoryTest {

    @Test
    void a_known_category_is_accepted_and_normalised() {
        assertThat(DowntimeCategory.validate("BREAKDOWN")).isEqualTo("BREAKDOWN");
        assertThat(DowntimeCategory.validate("  SETUP_CHANGEOVER ")).isEqualTo("SETUP_CHANGEOVER");
    }

    @Test
    void no_category_is_allowed_and_stays_unset() {
        assertThat(DowntimeCategory.validate(null)).isNull();
        assertThat(DowntimeCategory.validate("  ")).isNull();
    }

    @Test
    void an_unknown_category_is_rejected_with_the_allowed_values() {
        assertThatThrownBy(() -> DowntimeCategory.validate("COFFEE_BREAK"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("COFFEE_BREAK").hasMessageContaining("BREAKDOWN");
    }

    @Test
    void only_planned_maintenance_is_a_planned_loss_and_unknown_stored_values_count_as_other() {
        for (DowntimeCategory c : DowntimeCategory.values()) {
            assertThat(c.isPlanned()).isEqualTo(c == DowntimeCategory.PLANNED_MAINTENANCE);
        }
        assertThat(DowntimeCategory.parseOrOther(null)).isEqualTo(DowntimeCategory.OTHER);
        assertThat(DowntimeCategory.parseOrOther("legacy free text")).isEqualTo(DowntimeCategory.OTHER);
        assertThat(DowntimeCategory.parseOrOther("MINOR_STOP")).isEqualTo(DowntimeCategory.MINOR_STOP);
    }
}

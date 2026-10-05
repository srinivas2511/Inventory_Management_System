package com.springmfg.ims.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.maintenance.domain.MaintenanceStatus;
import com.springmfg.ims.maintenance.service.MaintenanceCostCalculator;

class MaintenanceCostCalculatorTest {

    @Test
    void labour_cost_is_minutes_times_hourly_rate_over_sixty() {
        // 90 min at 250 per hour = 1.5 h x 250 = 375
        assertThat(MaintenanceCostCalculator.labourCost(90, new BigDecimal("250.0000"))).isEqualByComparingTo("375");
    }

    @Test
    void labour_cost_is_computed_from_minutes_and_rounded_half_up_to_four_decimals() {
        // 7 min x 250 / 60 = 29.166666... -> 29.1667
        assertThat(MaintenanceCostCalculator.labourCost(7, new BigDecimal("250"))).isEqualTo(new BigDecimal("29.1667"));
        // 1 min x 100 / 60 = 1.666666... -> 1.6667
        assertThat(MaintenanceCostCalculator.labourCost(1, new BigDecimal("100"))).isEqualTo(new BigDecimal("1.6667"));
    }

    @Test
    void labour_hours_are_rounded_half_up_to_two_decimals() {
        assertThat(MaintenanceCostCalculator.labourHours(90)).isEqualTo(new BigDecimal("1.50"));
        assertThat(MaintenanceCostCalculator.labourHours(7)).isEqualTo(new BigDecimal("0.12"));   // 0.11666 -> 0.12
    }

    @Test
    void no_rate_or_no_minutes_is_zero_cost() {
        assertThat(MaintenanceCostCalculator.labourCost(120, null)).isEqualByComparingTo("0");
        assertThat(MaintenanceCostCalculator.labourCost(0, new BigDecimal("250"))).isEqualByComparingTo("0");
    }

    @Test
    void request_state_machine_allows_only_the_documented_moves() {
        assertThat(MaintenanceStatus.OPEN.next()).containsExactlyInAnyOrder(MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.CANCELLED);
        assertThat(MaintenanceStatus.IN_PROGRESS.next()).containsExactlyInAnyOrder(MaintenanceStatus.COMPLETED,
                MaintenanceStatus.CANCELLED);
        assertThat(MaintenanceStatus.COMPLETED.next()).isEmpty();
        assertThat(MaintenanceStatus.CANCELLED.next()).isEmpty();
        assertThat(MaintenanceStatus.OPEN.transitionTo(MaintenanceStatus.IN_PROGRESS))
                .isEqualTo(MaintenanceStatus.IN_PROGRESS);
    }

    @Test
    void a_request_cannot_skip_in_progress_or_be_reopened() {
        assertThatThrownBy(() -> MaintenanceStatus.OPEN.transitionTo(MaintenanceStatus.COMPLETED))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("OPEN")
                .hasMessageContaining("COMPLETED");
        assertThatThrownBy(() -> MaintenanceStatus.COMPLETED.transitionTo(MaintenanceStatus.OPEN))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> MaintenanceStatus.CANCELLED.transitionTo(MaintenanceStatus.IN_PROGRESS))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void work_can_be_booked_only_while_the_request_is_open_or_in_progress() {
        assertThat(MaintenanceStatus.OPEN.acceptsWork()).isTrue();
        assertThat(MaintenanceStatus.IN_PROGRESS.acceptsWork()).isTrue();
        assertThat(MaintenanceStatus.COMPLETED.acceptsWork()).isFalse();
        assertThat(MaintenanceStatus.CANCELLED.acceptsWork()).isFalse();
    }

    @Test
    void an_unknown_status_is_a_validation_error_not_a_free_form_update() {
        assertThatThrownBy(() -> MaintenanceStatus.parse("DONE")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DONE");
        assertThatThrownBy(() -> MaintenanceStatus.parse(null)).isInstanceOf(BusinessRuleException.class);
    }
}

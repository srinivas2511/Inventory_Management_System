package com.mfg.ims.qualityplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.qualityplus.domain.CapaStatus;
import com.mfg.ims.qualityplus.domain.ComplaintStatus;
import com.mfg.ims.qualityplus.domain.FaiStatus;

/** The CAPA, complaint and FAI state machines (CLAUDE.md rule 9). */
class QualityPlusStateMachineTest {

    // ---- CAPA ----------------------------------------------------------------------------------------------------

    @Test
    void capa_walks_forward_one_step_at_a_time() {
        CapaStatus s = CapaStatus.OPEN;
        s = s.transitionTo(CapaStatus.ROOT_CAUSE_ANALYSIS);
        s = s.transitionTo(CapaStatus.ACTION_IN_PROGRESS);
        s = s.transitionTo(CapaStatus.VERIFICATION);
        s = s.transitionTo(CapaStatus.CLOSED);
        assertThat(s).isEqualTo(CapaStatus.CLOSED);
    }

    @Test
    void capa_failed_verification_returns_to_action() {
        assertThat(CapaStatus.VERIFICATION.next())
                .containsExactlyInAnyOrder(CapaStatus.CLOSED, CapaStatus.ACTION_IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(CapaStatus.class)
    void capa_cannot_skip_steps_or_leave_closed(CapaStatus from) {
        for (CapaStatus to : CapaStatus.values()) {
            if (from.next().contains(to)) {
                assertThat(from.transitionTo(to)).isEqualTo(to);
            } else {
                assertThatThrownBy(() -> from.transitionTo(to)).isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
            }
        }
        assertThat(CapaStatus.CLOSED.next()).isEmpty();
        assertThatThrownBy(() -> CapaStatus.OPEN.transitionTo(CapaStatus.CLOSED))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- complaints ----------------------------------------------------------------------------------------------

    @Test
    void complaint_walks_open_to_closed_and_a_resolved_one_can_reopen() {
        ComplaintStatus s = ComplaintStatus.OPEN.transitionTo(ComplaintStatus.IN_PROGRESS)
                .transitionTo(ComplaintStatus.RESOLVED);
        assertThat(s.next()).containsExactlyInAnyOrder(ComplaintStatus.CLOSED, ComplaintStatus.IN_PROGRESS);
        assertThat(s.transitionTo(ComplaintStatus.CLOSED)).isEqualTo(ComplaintStatus.CLOSED);
    }

    @ParameterizedTest
    @EnumSource(ComplaintStatus.class)
    void complaint_rejects_every_undeclared_move(ComplaintStatus from) {
        for (ComplaintStatus to : ComplaintStatus.values()) {
            if (!from.next().contains(to)) {
                assertThatThrownBy(() -> from.transitionTo(to)).isInstanceOf(BusinessRuleException.class);
            }
        }
        assertThat(ComplaintStatus.CLOSED.next()).isEmpty();
        assertThatThrownBy(() -> ComplaintStatus.OPEN.transitionTo(ComplaintStatus.RESOLVED))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- FAI -----------------------------------------------------------------------------------------------------

    @Test
    void fai_ends_in_passed_or_failed_and_stays_there() {
        assertThat(FaiStatus.REQUIRED.next()).containsExactly(FaiStatus.IN_PROGRESS);
        assertThat(FaiStatus.IN_PROGRESS.next()).isEqualTo(EnumSet.of(FaiStatus.PASSED, FaiStatus.FAILED));
        assertThat(FaiStatus.PASSED.next()).isEmpty();
        assertThat(FaiStatus.FAILED.next()).isEmpty();
        assertThatThrownBy(() -> FaiStatus.REQUIRED.transitionTo(FaiStatus.PASSED))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> FaiStatus.FAILED.transitionTo(FaiStatus.IN_PROGRESS))
                .isInstanceOf(BusinessRuleException.class);
    }
}

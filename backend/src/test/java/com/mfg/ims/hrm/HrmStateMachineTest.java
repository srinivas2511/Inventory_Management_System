package com.mfg.ims.hrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.hrm.domain.LeaveStatus;
import com.mfg.ims.hrm.domain.PayrollStatus;

class HrmStateMachineTest {

    @Test
    void pending_leave_can_be_approved_rejected_or_cancelled() {
        assertThat(LeaveStatus.PENDING.next())
                .containsExactlyInAnyOrder(LeaveStatus.APPROVED, LeaveStatus.REJECTED, LeaveStatus.CANCELLED);
    }

    @Test
    void approved_leave_can_only_be_cancelled() {
        assertThat(LeaveStatus.APPROVED.next()).containsExactly(LeaveStatus.CANCELLED);
        assertThatThrownBy(() -> LeaveStatus.APPROVED.transitionTo(LeaveStatus.REJECTED))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    void rejected_and_cancelled_leave_are_terminal() {
        assertThat(LeaveStatus.REJECTED.next()).isEmpty();
        assertThat(LeaveStatus.CANCELLED.next()).isEmpty();
        assertThatThrownBy(() -> LeaveStatus.CANCELLED.transitionTo(LeaveStatus.APPROVED))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void payroll_moves_draft_to_completed_to_paid_and_never_back() {
        assertThat(PayrollStatus.DRAFT.transitionTo(PayrollStatus.COMPLETED)).isEqualTo(PayrollStatus.COMPLETED);
        assertThat(PayrollStatus.COMPLETED.transitionTo(PayrollStatus.PAID)).isEqualTo(PayrollStatus.PAID);
        assertThatThrownBy(() -> PayrollStatus.DRAFT.transitionTo(PayrollStatus.PAID))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayrollStatus.PAID.transitionTo(PayrollStatus.DRAFT))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayrollStatus.COMPLETED.transitionTo(PayrollStatus.DRAFT))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void only_approved_and_paid_payroll_lock_the_period() {
        assertThat(PayrollStatus.DRAFT.locksPeriod()).isFalse();
        assertThat(PayrollStatus.COMPLETED.locksPeriod()).isTrue();
        assertThat(PayrollStatus.PAID.locksPeriod()).isTrue();
    }
}

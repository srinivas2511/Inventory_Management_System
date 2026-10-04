package com.springmfg.ims.plm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.plm.domain.EcoStatus;
import com.springmfg.ims.plm.domain.ProductLifecycleStatus;

class PlmStateMachineTest {

    @Test
    void ecoHappyPathRunsDraftReviewApprovedImplementingImplemented() {
        EcoStatus s = EcoStatus.DRAFT;
        s = s.transitionTo(EcoStatus.REVIEW);
        s = s.transitionTo(EcoStatus.APPROVED);
        s = s.transitionTo(EcoStatus.IMPLEMENTING);
        s = s.transitionTo(EcoStatus.IMPLEMENTED);
        assertThat(s).isEqualTo(EcoStatus.IMPLEMENTED);
    }

    @Test
    void reviewCanBeReturnedToDraft() {
        assertThat(EcoStatus.REVIEW.transitionTo(EcoStatus.DRAFT)).isEqualTo(EcoStatus.DRAFT);
    }

    @Test
    void approvalCannotBeSkippedAndImplementationNeedsApproval() {
        assertThatThrownBy(() -> EcoStatus.DRAFT.transitionTo(EcoStatus.APPROVED))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> EcoStatus.REVIEW.transitionTo(EcoStatus.IMPLEMENTING))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> EcoStatus.DRAFT.transitionTo(EcoStatus.IMPLEMENTED))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EcoStatus.class, names = { "DRAFT", "REVIEW", "APPROVED" })
    void anythingBeforeImplementationCanBeCancelled(EcoStatus from) {
        assertThat(from.transitionTo(EcoStatus.CANCELLED)).isEqualTo(EcoStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = EcoStatus.class, names = { "IMPLEMENTING", "IMPLEMENTED", "CANCELLED" })
    void implementationAndTerminalStatesCannotBeCancelled(EcoStatus from) {
        assertThatThrownBy(() -> from.transitionTo(EcoStatus.CANCELLED)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EcoStatus.class, names = { "IMPLEMENTED", "CANCELLED" })
    void terminalEcoStatesHaveNoExit(EcoStatus terminal) {
        assertThat(terminal.next()).isEmpty();
        for (EcoStatus target : EnumSet.allOf(EcoStatus.class)) {
            assertThatThrownBy(() -> terminal.transitionTo(target))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        }
    }

    @Test
    void lifecycleMovesOneStepForwardAlongTheLadder() {
        ProductLifecycleStatus s = ProductLifecycleStatus.DEVELOPMENT;
        for (ProductLifecycleStatus expected : new ProductLifecycleStatus[] { ProductLifecycleStatus.PROTOTYPE,
                ProductLifecycleStatus.APPROVED, ProductLifecycleStatus.ACTIVE, ProductLifecycleStatus.PHASE_OUT,
                ProductLifecycleStatus.OBSOLETE }) {
            assertThat(s.next()).contains(expected);
            s = s.transitionTo(expected);
        }
        assertThat(s.next()).isEmpty();
    }

    @Test
    void lifecycleCannotSkipStepsOrGoBackwards() {
        assertThatThrownBy(() -> ProductLifecycleStatus.DEVELOPMENT.transitionTo(ProductLifecycleStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ProductLifecycleStatus.ACTIVE.transitionTo(ProductLifecycleStatus.PROTOTYPE))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ProductLifecycleStatus.ACTIVE.transitionTo(ProductLifecycleStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void obsoleteIsTerminal() {
        for (ProductLifecycleStatus target : ProductLifecycleStatus.values()) {
            assertThatThrownBy(() -> ProductLifecycleStatus.OBSOLETE.transitionTo(target))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}

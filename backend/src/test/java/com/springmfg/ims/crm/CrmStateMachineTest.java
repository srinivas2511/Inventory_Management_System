package com.springmfg.ims.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.springmfg.ims.crm.domain.LeadStatus;
import com.springmfg.ims.crm.domain.OpportunityStage;
import com.springmfg.ims.crm.domain.QuotationStatus;

/** Pure tests of the three CRM state machines (CLAUDE.md rule 9: no free-form status updates). */
class CrmStateMachineTest {

    @Test
    @DisplayName("lead: NEW -> CONTACTED -> QUALIFIED -> CONVERTED is the only forward path")
    void leadHappyPath() {
        assertThat(LeadStatus.NEW.canMoveTo(LeadStatus.CONTACTED)).isTrue();
        assertThat(LeadStatus.CONTACTED.canMoveTo(LeadStatus.QUALIFIED)).isTrue();
        assertThat(LeadStatus.QUALIFIED.canMoveTo(LeadStatus.CONVERTED)).isTrue();
    }

    @Test
    @DisplayName("lead: steps cannot be skipped")
    void leadCannotSkip() {
        assertThat(LeadStatus.NEW.canMoveTo(LeadStatus.QUALIFIED)).isFalse();
        assertThat(LeadStatus.NEW.canMoveTo(LeadStatus.CONVERTED)).isFalse();
        assertThat(LeadStatus.CONTACTED.canMoveTo(LeadStatus.CONVERTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"NEW", "CONTACTED", "QUALIFIED"})
    @DisplayName("lead: any open lead can be disqualified")
    void openLeadsCanBeDisqualified(LeadStatus status) {
        assertThat(status.canMoveTo(LeadStatus.UNQUALIFIED)).isTrue();
        assertThat(status.isOpen()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"UNQUALIFIED", "CONVERTED"})
    @DisplayName("lead: UNQUALIFIED and CONVERTED are terminal")
    void leadTerminalStates(LeadStatus status) {
        assertThat(status.next()).isEmpty();
        assertThat(status.isOpen()).isFalse();
    }

    @Test
    @DisplayName("opportunity: PROSPECT -> QUALIFIED -> PROPOSAL -> NEGOTIATION -> CLOSED_WON")
    void opportunityHappyPath() {
        assertThat(OpportunityStage.PROSPECT.canMoveTo(OpportunityStage.QUALIFIED)).isTrue();
        assertThat(OpportunityStage.QUALIFIED.canMoveTo(OpportunityStage.PROPOSAL)).isTrue();
        assertThat(OpportunityStage.PROPOSAL.canMoveTo(OpportunityStage.NEGOTIATION)).isTrue();
        assertThat(OpportunityStage.NEGOTIATION.canMoveTo(OpportunityStage.CLOSED_WON)).isTrue();
    }

    @Test
    @DisplayName("opportunity: cannot win before a proposal exists, and cannot move backwards")
    void opportunityGuards() {
        assertThat(OpportunityStage.PROSPECT.canMoveTo(OpportunityStage.CLOSED_WON)).isFalse();
        assertThat(OpportunityStage.QUALIFIED.canMoveTo(OpportunityStage.CLOSED_WON)).isFalse();
        assertThat(OpportunityStage.NEGOTIATION.canMoveTo(OpportunityStage.PROPOSAL)).isFalse();
        assertThat(OpportunityStage.PROPOSAL.canMoveTo(OpportunityStage.PROSPECT)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"PROSPECT", "QUALIFIED", "PROPOSAL", "NEGOTIATION"})
    @DisplayName("opportunity: every open stage can be closed as lost")
    void openStagesCanBeLost(OpportunityStage stage) {
        assertThat(stage.canMoveTo(OpportunityStage.CLOSED_LOST)).isTrue();
        assertThat(stage.isOpen()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OpportunityStage.class, names = {"CLOSED_WON", "CLOSED_LOST"})
    @DisplayName("opportunity: closed stages are terminal")
    void closedStagesAreTerminal(OpportunityStage stage) {
        assertThat(stage.next()).isEmpty();
        assertThat(stage.isOpen()).isFalse();
    }

    @Test
    @DisplayName("opportunity: stage probabilities rise along the pipeline")
    void probabilitiesIncrease() {
        assertThat(OpportunityStage.PROSPECT.defaultProbability())
                .isLessThan(OpportunityStage.QUALIFIED.defaultProbability());
        assertThat(OpportunityStage.QUALIFIED.defaultProbability())
                .isLessThan(OpportunityStage.PROPOSAL.defaultProbability());
        assertThat(OpportunityStage.PROPOSAL.defaultProbability())
                .isLessThan(OpportunityStage.NEGOTIATION.defaultProbability());
        assertThat(OpportunityStage.CLOSED_WON.defaultProbability()).isEqualTo(100);
        assertThat(OpportunityStage.CLOSED_LOST.defaultProbability()).isZero();
    }

    @Test
    @DisplayName("quotation: DRAFT -> SENT -> ACCEPTED -> CONVERTED")
    void quotationHappyPath() {
        assertThat(QuotationStatus.DRAFT.canMoveTo(QuotationStatus.SENT)).isTrue();
        assertThat(QuotationStatus.SENT.canMoveTo(QuotationStatus.ACCEPTED)).isTrue();
        assertThat(QuotationStatus.ACCEPTED.canMoveTo(QuotationStatus.CONVERTED)).isTrue();
    }

    @Test
    @DisplayName("quotation: only an ACCEPTED quotation converts; drafts and rejections do not")
    void onlyAcceptedConverts() {
        for (QuotationStatus s : EnumSet.complementOf(EnumSet.of(QuotationStatus.ACCEPTED))) {
            assertThat(s.canMoveTo(QuotationStatus.CONVERTED)).as("%s -> CONVERTED", s).isFalse();
        }
    }

    @Test
    @DisplayName("quotation: a customer response needs a sent quotation")
    void responseNeedsSent() {
        assertThat(QuotationStatus.DRAFT.canMoveTo(QuotationStatus.ACCEPTED)).isFalse();
        assertThat(QuotationStatus.DRAFT.canMoveTo(QuotationStatus.REJECTED)).isFalse();
        assertThat(QuotationStatus.SENT.canMoveTo(QuotationStatus.REJECTED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = QuotationStatus.class, names = {"REJECTED", "EXPIRED", "CONVERTED"})
    @DisplayName("quotation: REJECTED, EXPIRED and CONVERTED are terminal")
    void quotationTerminalStates(QuotationStatus status) {
        assertThat(status.next()).isEmpty();
    }

    @Test
    @DisplayName("quotation: only DRAFT and SENT can expire")
    void expiry() {
        assertThat(QuotationStatus.DRAFT.canMoveTo(QuotationStatus.EXPIRED)).isTrue();
        assertThat(QuotationStatus.SENT.canMoveTo(QuotationStatus.EXPIRED)).isTrue();
        assertThat(QuotationStatus.ACCEPTED.canMoveTo(QuotationStatus.EXPIRED)).isFalse();
    }
}

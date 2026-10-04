package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.springmfg.ims.finance.gl.domain.InventoryAccountAssignment;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.InventoryAccountAssignmentRepository;
import com.springmfg.ims.finance.gl.service.AccountingBridgeService;
import com.springmfg.ims.finance.gl.service.AccountingFailureReporter;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.inventory.domain.InventoryTransaction;
import com.springmfg.ims.inventory.event.StockPostedEvent;

/** R10-1.2: the bridge copies cost/profit centre defaults from the account assignment onto both journal lines. */
class AccountingBridgeServiceTest {

    private InventoryAccountAssignmentRepository assignmentRepo;
    private JournalService journalService;
    private AccountingFailureReporter failureReporter;
    private AccountingBridgeService bridge;

    @BeforeEach
    void setUp() {
        assignmentRepo = mock(InventoryAccountAssignmentRepository.class);
        journalService = mock(JournalService.class);
        failureReporter = mock(AccountingFailureReporter.class);
        bridge = new AccountingBridgeService(assignmentRepo, journalService, failureReporter);
    }

    private InventoryTransaction transaction(String type, BigDecimal totalCost) {
        InventoryTransaction tx = mock(InventoryTransaction.class);
        when(tx.getTransactionType()).thenReturn(type);
        when(tx.getItemType()).thenReturn("MATERIAL");
        when(tx.getTotalCost()).thenReturn(totalCost);
        when(tx.getId()).thenReturn(42L);
        when(tx.getTransactionNumber()).thenReturn("TX-1");
        when(tx.getReferenceType()).thenReturn("PRODUCTION_ORDER");
        when(tx.getReferenceId()).thenReturn(7L);
        when(tx.getCreatedBy()).thenReturn(3L);
        return tx;
    }

    @Test
    void assignmentDimensionsAreCopiedToBothLines() {
        InventoryAccountAssignment assignment = new InventoryAccountAssignment();
        assignment.setGlAccountDebitId(10L);
        assignment.setGlAccountCreditId(20L);
        assignment.setCostCentreId(5L);
        assignment.setProfitCentreId(6L);
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(1L, "MATERIAL_ISSUE", "MATERIAL"))
                .thenReturn(Optional.of(assignment));

        bridge.onStockPosted(new StockPostedEvent(transaction("MATERIAL_ISSUE", new BigDecimal("-250.0000"))));

        ArgumentCaptor<AutoJournalRequest> captor = ArgumentCaptor.forClass(AutoJournalRequest.class);
        verify(journalService).postAutoJournal(captor.capture());
        AutoJournalRequest req = captor.getValue();
        assertThat(req.lines()).hasSize(2);

        JournalLineRequest dr = req.lines().get(0);
        JournalLineRequest cr = req.lines().get(1);
        assertThat(dr.glAccountId()).isEqualTo(10L);
        assertThat(dr.debitAmount()).isEqualByComparingTo("250");
        assertThat(cr.glAccountId()).isEqualTo(20L);
        assertThat(cr.creditAmount()).isEqualByComparingTo("250");
        assertThat(req.lines()).allSatisfy(l -> {
            assertThat(l.costCentreId()).isEqualTo(5L);
            assertThat(l.profitCentreId()).isEqualTo(6L);
        });
    }

    @Test
    void assignmentWithoutDimensionsLeavesLinesUntagged() {
        InventoryAccountAssignment assignment = new InventoryAccountAssignment();
        assignment.setGlAccountDebitId(10L);
        assignment.setGlAccountCreditId(20L);
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(1L, "PURCHASE_RECEIPT", "MATERIAL"))
                .thenReturn(Optional.of(assignment));

        bridge.onStockPosted(new StockPostedEvent(transaction("PURCHASE_RECEIPT", new BigDecimal("100"))));

        ArgumentCaptor<AutoJournalRequest> captor = ArgumentCaptor.forClass(AutoJournalRequest.class);
        verify(journalService).postAutoJournal(captor.capture());
        assertThat(captor.getValue().lines()).allSatisfy(l -> {
            assertThat(l.costCentreId()).isNull();
            assertThat(l.profitCentreId()).isNull();
        });
    }

    @Test
    void missingAssignmentPostsNothingAndRaisesAnAlert() {
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(any(), any(), any())).thenReturn(Optional.empty());
        when(assignmentRepo.findByCompanyIdAndTransactionType(any(), any())).thenReturn(Optional.empty());

        bridge.onStockPosted(new StockPostedEvent(transaction("UNKNOWN_TYPE", new BigDecimal("100"))));

        verify(journalService, never()).postAutoJournal(any());
        verify(failureReporter).missingJournal(eq(42L), eq("UNKNOWN_TYPE"), eq("TX-1"), anyString());
    }

    @Test
    void aFailedJournalIsReportedAndNeverPropagates() {
        InventoryAccountAssignment assignment = new InventoryAccountAssignment();
        assignment.setGlAccountDebitId(10L);
        assignment.setGlAccountCreditId(20L);
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(1L, "PURCHASE_RECEIPT", "MATERIAL"))
                .thenReturn(Optional.of(assignment));
        doThrow(new IllegalStateException("No open fiscal period")).when(journalService).postAutoJournal(any());

        bridge.onStockPosted(new StockPostedEvent(transaction("PURCHASE_RECEIPT", new BigDecimal("100"))));

        verify(failureReporter).missingJournal(eq(42L), eq("PURCHASE_RECEIPT"), eq("TX-1"),
                eq("No open fiscal period"));
    }

    @Test
    void aFailingAlertWriteDoesNotHideTheOriginalProblemOrPropagate() {
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(any(), any(), any())).thenReturn(Optional.empty());
        when(assignmentRepo.findByCompanyIdAndTransactionType(any(), any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("alert store down")).when(failureReporter)
                .missingJournal(any(), anyString(), anyString(), anyString());

        bridge.onStockPosted(new StockPostedEvent(transaction("UNKNOWN_TYPE", new BigDecimal("100"))));

        verify(journalService, never()).postAutoJournal(any());
    }

    @Test
    void zeroValueMovementHasNothingToAccountForAndRaisesNoAlert() {
        InventoryAccountAssignment assignment = new InventoryAccountAssignment();
        assignment.setGlAccountDebitId(10L);
        assignment.setGlAccountCreditId(20L);
        when(assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(1L, "PURCHASE_RECEIPT", "MATERIAL"))
                .thenReturn(Optional.of(assignment));

        bridge.onStockPosted(new StockPostedEvent(transaction("PURCHASE_RECEIPT", BigDecimal.ZERO)));

        verify(journalService, never()).postAutoJournal(any());
        verify(failureReporter, never()).missingJournal(any(), anyString(), anyString(), anyString());
    }
}

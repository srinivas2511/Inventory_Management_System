package com.springmfg.ims.finance.gl.service;

import com.springmfg.ims.finance.gl.domain.InventoryAccountAssignment;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.InventoryAccountAssignmentRepository;
import com.springmfg.ims.inventory.event.StockPostedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Listens for committed inventory transactions and auto-generates GL journal entries.
 * Fires after commit (AFTER_COMMIT) so it never throws back into inventory processing.
 */
@Service
public class AccountingBridgeService {

    private static final Logger log = LoggerFactory.getLogger(AccountingBridgeService.class);

    private final InventoryAccountAssignmentRepository assignmentRepo;
    private final JournalService journalService;

    public AccountingBridgeService(InventoryAccountAssignmentRepository assignmentRepo,
                                   JournalService journalService) {
        this.assignmentRepo = assignmentRepo;
        this.journalService = journalService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockPosted(StockPostedEvent event) {
        var tx = event.transaction();
        try {
            Optional<InventoryAccountAssignment> assignmentOpt =
                    assignmentRepo.findByCompanyIdAndTransactionTypeAndItemType(
                            1L, tx.getTransactionType(), tx.getItemType());

            if (assignmentOpt.isEmpty()) {
                assignmentOpt = assignmentRepo.findByCompanyIdAndTransactionType(
                        1L, tx.getTransactionType());
            }

            if (assignmentOpt.isEmpty()) {
                log.warn("No account assignment for transactionType={} itemType={}; skipping auto-journal",
                        tx.getTransactionType(), tx.getItemType());
                return;
            }

            InventoryAccountAssignment assignment = assignmentOpt.get();
            BigDecimal amount = tx.getTotalCost().abs();
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }

            List<JournalLineRequest> lines = List.of(
                    new JournalLineRequest(assignment.getGlAccountDebitId(), amount, BigDecimal.ZERO,
                            tx.getTransactionType() + " " + tx.getTransactionNumber(),
                            assignment.getCostCentreId(), assignment.getProfitCentreId()),
                    new JournalLineRequest(assignment.getGlAccountCreditId(), BigDecimal.ZERO, amount,
                            tx.getTransactionType() + " " + tx.getTransactionNumber(),
                            assignment.getCostCentreId(), assignment.getProfitCentreId())
            );

            AutoJournalRequest req = new AutoJournalRequest(
                    "AUTO",
                    "INVENTORY",
                    tx.getReferenceType(),
                    tx.getReferenceId(),
                    LocalDate.now(),
                    tx.getTransactionType() + " – " + tx.getTransactionNumber(),
                    tx.getCreatedBy(),
                    lines
            );

            journalService.postAutoJournal(req);

        } catch (Exception e) {
            log.error("AccountingBridgeService failed for transaction {} — GL entry skipped: {}",
                    tx.getTransactionNumber(), e.getMessage(), e);
        }
    }
}

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
    private final AccountingFailureReporter failureReporter;
    private final List<JournalDimensionResolver> dimensionResolvers;

    public AccountingBridgeService(InventoryAccountAssignmentRepository assignmentRepo,
                                   JournalService journalService,
                                   AccountingFailureReporter failureReporter,
                                   List<JournalDimensionResolver> dimensionResolvers) {
        this.assignmentRepo = assignmentRepo;
        this.journalService = journalService;
        this.failureReporter = failureReporter;
        this.dimensionResolvers = dimensionResolvers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockPosted(StockPostedEvent event) {
        var tx = event.transaction();
        // A transfer only moves stock between locations of the same valuation, so it has no accounting effect.
        if ("STOCK_TRANSFER".equals(tx.getTransactionType())) {
            return;
        }
        // Consuming issued material only turns WIP stock into output: the GL was debited to WIP at issue and WIP is
        // credited when the output is received, so consumption itself has no journal.
        if ("MATERIAL_CONSUMPTION".equals(tx.getTransactionType())) {
            return;
        }
        // Final inspection (and the release of a held batch) writes rejected finished goods off with a SCRAP out-leg (Dr 5202 / Cr 1410) and records the
        // pieces in the scrap location with this zero-value in-leg; journalling it too would relieve WIP a second time.
        if ("PRODUCTION_REJECTION".equals(tx.getTransactionType())
                && ("QUALITY_INSPECTION".equals(tx.getReferenceType()) || "FG_BATCH".equals(tx.getReferenceType()))) {
            return;
        }
        // The in-leg of a material issue puts the same stock into the WIP location; the issue journal (Dr 1501 / Cr 1401)
        // already covers the move.
        if ("MATERIAL_RECEIPT".equals(tx.getTransactionType())) {
            return;
        }
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
                report(tx, "no GL account assignment for " + tx.getTransactionType() + "/" + tx.getItemType());
                return;
            }

            InventoryAccountAssignment assignment = assignmentOpt.get();
            BigDecimal amount = tx.getTotalCost().abs();
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                // A zero-value movement of real stock leaves the GL out of step with the ledger; never skip it silently.
                if (tx.getQuantity() != null && tx.getQuantity().signum() != 0) {
                    report(tx, "zero-value movement (unit cost 0), so the GL does not reflect this stock");
                }
                return;
            }

            Long costCentreId = costCentreFor(tx, assignment);
            List<JournalLineRequest> lines = List.of(
                    new JournalLineRequest(assignment.getGlAccountDebitId(), amount, BigDecimal.ZERO,
                            tx.getTransactionType() + " " + tx.getTransactionNumber(),
                            costCentreId, assignment.getProfitCentreId()),
                    new JournalLineRequest(assignment.getGlAccountCreditId(), BigDecimal.ZERO, amount,
                            tx.getTransactionType() + " " + tx.getTransactionNumber(),
                            costCentreId, assignment.getProfitCentreId())
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
            report(tx, e.getMessage());
        }
    }

    /** The document that caused the movement may know a better cost centre than the assignment's default. */
    private Long costCentreFor(com.springmfg.ims.inventory.domain.InventoryTransaction tx,
                               InventoryAccountAssignment assignment) {
        for (JournalDimensionResolver resolver : dimensionResolvers) {
            Optional<Long> resolved = resolver.costCentreFor(tx.getReferenceType(), tx.getReferenceId());
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return assignment.getCostCentreId();
    }

    /** The stock movement already committed, so the gap is made visible as an alert instead of an error. */
    private void report(com.springmfg.ims.inventory.domain.InventoryTransaction tx, String reason) {
        try {
            failureReporter.missingJournal(tx.getId(), tx.getTransactionType(), tx.getTransactionNumber(), reason);
        } catch (Exception alertFailure) {
            log.error("Could not raise the MISSING_JOURNAL alert for transaction {}: {}", tx.getTransactionNumber(),
                    alertFailure.getMessage(), alertFailure);
        }
    }
}

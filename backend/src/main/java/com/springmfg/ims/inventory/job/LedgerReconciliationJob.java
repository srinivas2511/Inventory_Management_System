package com.springmfg.ims.inventory.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.inventory.domain.Alert;
import com.springmfg.ims.inventory.domain.InventoryBalance;
import com.springmfg.ims.inventory.repository.AlertRepository;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.inventory.repository.InventoryTransactionRepository;

/**
 * Nightly job: for each balance row, verify Σ(ledger quantities) = qty_on_hand.
 * Raises LEDGER_MISMATCH alert if discrepancy found; resolves it when corrected.
 * ShedLock ensures only one instance runs in a multi-pod deployment.
 */
@Component
public class LedgerReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationJob.class);

    private final InventoryBalanceRepository balanceRepo;
    private final InventoryTransactionRepository txRepo;
    private final AlertRepository alertRepo;

    public LedgerReconciliationJob(InventoryBalanceRepository balanceRepo,
                                    InventoryTransactionRepository txRepo,
                                    AlertRepository alertRepo) {
        this.balanceRepo = balanceRepo;
        this.txRepo = txRepo;
        this.alertRepo = alertRepo;
    }

    @Scheduled(cron = "0 0 1 * * *") // 01:00 UTC every night
    @SchedulerLock(name = "ledger_reconciliation", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Transactional
    public void reconcile() {
        log.info("Starting nightly ledger reconciliation");
        List<InventoryBalance> balances = balanceRepo.findAll();
        int mismatches = 0;
        int resolved = 0;

        for (InventoryBalance balance : balances) {
            BigDecimal ledgerSum = txRepo.sumBalanceLedger(
                    balance.getWarehouseId(), balance.getLocationId(),
                    balance.getItemType(), balance.getMaterialId(), balance.getProductId(),
                    balance.getMaterialBatchId(), balance.getFgBatchId(),
                    balance.getStockStatus(), Instant.now());

            if (ledgerSum == null) ledgerSum = BigDecimal.ZERO;

            BigDecimal diff = balance.getQtyOnHand().subtract(ledgerSum).abs();
            String openKey = "LEDGER_MISMATCH:BALANCE:" + balance.getId();

            if (diff.compareTo(new BigDecimal("0.001")) > 0) {
                mismatches++;
                log.warn("Ledger mismatch on balance {} — on_hand={} ledger_sum={} diff={}",
                        balance.getId(), balance.getQtyOnHand(), ledgerSum, diff);
                raiseOrUpdate(openKey, balance.getId(),
                        "Balance " + balance.getId() + ": on_hand=" + balance.getQtyOnHand()
                        + " vs ledger_sum=" + ledgerSum + " (diff=" + diff + ")");
            } else {
                alertRepo.findOpenByKey(openKey).ifPresent(a -> {
                    a.setResolvedAt(Instant.now());
                    alertRepo.save(a);
                });
                resolved++;
            }
        }

        log.info("Ledger reconciliation complete — {} balances checked, {} mismatches, {} resolved",
                balances.size(), mismatches, resolved);
    }

    private void raiseOrUpdate(String openKey, Long balanceId, String message) {
        alertRepo.findOpenByKey(openKey).ifPresentOrElse(
                existing -> {
                    existing.setLastSeenAt(Instant.now());
                    existing.setMessage(message);
                    alertRepo.save(existing);
                },
                () -> {
                    Alert alert = new Alert();
                    alert.setAlertType("LEDGER_MISMATCH");
                    alert.setSeverity("CRITICAL");
                    alert.setEntityType("INVENTORY_BALANCE");
                    alert.setEntityId(balanceId);
                    alert.setOpenKey(openKey);
                    alert.setMessage(message);
                    alertRepo.save(alert);
                });
    }
}

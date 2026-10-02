package com.springmfg.ims.inventory.event;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.Alert;
import com.springmfg.ims.inventory.domain.InventoryTransaction;
import com.springmfg.ims.inventory.repository.AlertRepository;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;

/**
 * Listens to StockPostedEvent and raises LOW_STOCK / OVERSTOCK alerts.
 * Runs in a new transaction so a failing alert never rolls back the posting.
 */
@Component
public class StockAlertListener {

    private static final Logger log = LoggerFactory.getLogger(StockAlertListener.class);

    private final AlertRepository alertRepo;
    private final InventoryBalanceRepository balanceRepo;
    private final SystemSettingService settings;

    public StockAlertListener(AlertRepository alertRepo,
                               InventoryBalanceRepository balanceRepo,
                               SystemSettingService settings) {
        this.alertRepo = alertRepo;
        this.balanceRepo = balanceRepo;
        this.settings = settings;
    }

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockPosted(StockPostedEvent event) {
        try {
            checkLowStock(event.transaction());
        } catch (Exception e) {
            log.warn("Alert check failed for transaction {}: {}", event.transaction().getId(), e.getMessage());
        }
    }

    private void checkLowStock(InventoryTransaction tx) {
        if (tx.getMaterialId() == null) return;

        BigDecimal threshold = settings.getDecimal(
                "inventory.low_stock.threshold." + tx.getMaterialId(),
                settings.getDecimal("inventory.low_stock.threshold.default", BigDecimal.ZERO));

        if (threshold.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal totalAvailable = balanceRepo.sumQtyByMaterialAndStatus(tx.getMaterialId(), "AVAILABLE");
        if (totalAvailable == null) totalAvailable = BigDecimal.ZERO;

        String openKey = "LOW_STOCK:MATERIAL:" + tx.getMaterialId();

        if (totalAvailable.compareTo(threshold) <= 0) {
            raiseOrUpdate(openKey, "LOW_STOCK", "HIGH", "MATERIAL", tx.getMaterialId(),
                    "Material " + tx.getMaterialId() + " available qty " + totalAvailable + " ≤ threshold " + threshold);
        } else {
            // Resolve if previously raised
            alertRepo.findOpenByKey(openKey).ifPresent(a -> {
                a.setResolvedAt(Instant.now());
                alertRepo.save(a);
            });
        }
    }

    private void raiseOrUpdate(String openKey, String alertType, String severity,
                                String entityType, Long entityId, String message) {
        alertRepo.findOpenByKey(openKey).ifPresentOrElse(
                existing -> {
                    existing.setLastSeenAt(Instant.now());
                    existing.setMessage(message);
                    alertRepo.save(existing);
                },
                () -> {
                    Alert alert = new Alert();
                    alert.setAlertType(alertType);
                    alert.setSeverity(severity);
                    alert.setEntityType(entityType);
                    alert.setEntityId(entityId);
                    alert.setOpenKey(openKey);
                    alert.setMessage(message);
                    alertRepo.save(alert);
                });
    }
}

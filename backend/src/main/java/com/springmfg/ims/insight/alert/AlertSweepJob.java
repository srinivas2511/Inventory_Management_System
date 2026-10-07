package com.springmfg.ims.insight.alert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.Alert;
import com.springmfg.ims.inventory.repository.AlertRepository;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.procurement.domain.PurchaseOrder;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;
import com.springmfg.ims.production.domain.ProductionOrder;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;

@Component
public class AlertSweepJob {

    private static final Logger log = LoggerFactory.getLogger(AlertSweepJob.class);

    private final AlertRepository alertRepo;
    private final MaterialRepository materialRepo;
    private final InventoryBalanceRepository balanceRepo;
    private final PurchaseOrderRepository poRepo;
    private final ProductionOrderRepository productionOrderRepo;
    private final QualityInspectionRepository inspectionRepo;
    private final SystemSettingService settings;

    public AlertSweepJob(AlertRepository alertRepo,
                         MaterialRepository materialRepo,
                         InventoryBalanceRepository balanceRepo,
                         PurchaseOrderRepository poRepo,
                         ProductionOrderRepository productionOrderRepo,
                         QualityInspectionRepository inspectionRepo,
                         SystemSettingService settings) {
        this.alertRepo = alertRepo;
        this.materialRepo = materialRepo;
        this.balanceRepo = balanceRepo;
        this.poRepo = poRepo;
        this.productionOrderRepo = productionOrderRepo;
        this.inspectionRepo = inspectionRepo;
        this.settings = settings;
    }

    @Scheduled(fixedDelay = 900_000)
    @SchedulerLock(name = "alert_sweep", lockAtLeastFor = "PT5M", lockAtMostFor = "PT14M")
    @Transactional
    public void sweep() {
        runSweep();
    }

    /**
     * One sweep without the scheduler lock. The scheduled run holds the lock for at least five minutes, so a caller that
     * needs a sweep now (the demo loader, once its data exists) would otherwise be skipped silently.
     */
    @Transactional
    public void runSweep() {
        if (!settings.getBoolean("alert.low_stock.enabled", true)) return;
        log.info("Starting alert sweep");

        sweepLowStock();
        sweepOverduePOs();
        sweepProductionDelays();
        sweepPendingInspections();

        log.info("Alert sweep complete");
    }

    private void sweepLowStock() {
        List<Material> materials = materialRepo.findAllByActiveTrue();
        for (Material m : materials) {
            java.math.BigDecimal available = balanceRepo.sumQtyByMaterialAndStatus(m.getId(), "AVAILABLE");
            if (available == null) available = java.math.BigDecimal.ZERO;
            String entityType = "MATERIAL";
            Long entityId = m.getId();

            List<Alert> open = alertRepo.findOpenByTypeAndEntity("LOW_STOCK", entityType, entityId);
            if (available.compareTo(m.getMinStock()) < 0) {
                if (open.isEmpty()) {
                    raise("LOW_STOCK", "MEDIUM", entityType, entityId,
                            "Material '" + m.getName() + "' available=" + available + " < min=" + m.getMinStock());
                }
            } else {
                open.forEach(a -> {
                    a.setResolvedAt(Instant.now());
                    a.setStatus("RESOLVED");
                    alertRepo.save(a);
                });
            }
        }
    }

    private void sweepOverduePOs() {
        LocalDate today = LocalDate.now();
        List<PurchaseOrder> overdue = poRepo.findOverdue(today);
        for (PurchaseOrder po : overdue) {
            List<Alert> open = alertRepo.findOpenByTypeAndEntity("OVERDUE_PO", "PURCHASE_ORDER", po.getId());
            if (open.isEmpty()) {
                raise("OVERDUE_PO", "MEDIUM", "PURCHASE_ORDER", po.getId(),
                        "PO " + po.getPoNumber() + " expected " + po.getExpectedDeliveryDate() + " is overdue");
            }
        }
    }

    private void sweepProductionDelays() {
        LocalDate today = LocalDate.now();
        List<ProductionOrder> delayed = productionOrderRepo.findDelayed(today);
        for (ProductionOrder po : delayed) {
            List<Alert> open = alertRepo.findOpenByTypeAndEntity("PRODUCTION_DELAY", "PRODUCTION_ORDER", po.getId());
            if (open.isEmpty()) {
                raise("PRODUCTION_DELAY", "MEDIUM", "PRODUCTION_ORDER", po.getId(),
                        "Production order " + po.getOrderNumber() + " expected " + po.getExpectedCompletionDate() + " is delayed");
            }
        }
    }

    private void sweepPendingInspections() {
        int days = settings.getInt("alert.pending_inspection.days", 2);
        Instant cutoff = Instant.now().minusSeconds((long) days * 24 * 3600);
        List<Long> longPending = inspectionRepo.findIdsPendingBefore(cutoff);
        for (Long id : longPending) {
            List<Alert> open = alertRepo.findOpenByTypeAndEntity("PENDING_INSPECTION", "QUALITY_INSPECTION", id);
            if (open.isEmpty()) {
                raise("PENDING_INSPECTION", "HIGH", "QUALITY_INSPECTION", id,
                        "Inspection " + id + " has been pending for over " + days + " days");
            }
        }
    }

    private void raise(String type, String severity, String entityType, Long entityId, String message) {
        Alert a = new Alert();
        a.setAlertType(type);
        a.setSeverity(severity);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setOpenKey(type + ":" + entityType + ":" + entityId);
        a.setMessage(message);
        a.setStatus("OPEN");
        alertRepo.save(a);
    }
}

package com.springmfg.ims.insight.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.repository.AlertRepository;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.sales.repository.SalesOrderRepository;

@Service
public class DashboardService {

    private final InventoryBalanceRepository balanceRepo;
    private final ProductionOrderRepository productionRepo;
    private final QualityInspectionRepository inspectionRepo;
    private final PurchaseOrderRepository poRepo;
    private final SalesOrderRepository salesRepo;
    private final AlertRepository alertRepo;
    private final SystemSettingService settings;

    public DashboardService(InventoryBalanceRepository balanceRepo,
                            ProductionOrderRepository productionRepo,
                            QualityInspectionRepository inspectionRepo,
                            PurchaseOrderRepository poRepo,
                            SalesOrderRepository salesRepo,
                            AlertRepository alertRepo,
                            SystemSettingService settings) {
        this.balanceRepo = balanceRepo;
        this.productionRepo = productionRepo;
        this.inspectionRepo = inspectionRepo;
        this.poRepo = poRepo;
        this.salesRepo = salesRepo;
        this.alertRepo = alertRepo;
        this.settings = settings;
    }

    @Cacheable("dashboardKpis")
    @Transactional(readOnly = true)
    public DashboardKpiDto getKpis() {
        // inventory
        long itemsWithStock = balanceRepo.countItemsWithStock();
        BigDecimal totalValue = balanceRepo.sumInventoryValue();
        if (totalValue == null) totalValue = BigDecimal.ZERO;

        // production
        long ordersInProgress = productionRepo.countInProgress();
        BigDecimal planned = productionRepo.sumPlannedQtyThisMonth();
        if (planned == null) planned = BigDecimal.ZERO;
        BigDecimal good = productionRepo.sumGoodQtyThisMonth();
        if (good == null) good = BigDecimal.ZERO;
        BigDecimal yieldPct = planned.compareTo(BigDecimal.ZERO) > 0
                ? good.divide(planned, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal target = settings.getDecimal("production.monthly_target", BigDecimal.ZERO);
        BigDecimal attainmentPct = target.signum() > 0
                ? good.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // quality
        long pending = inspectionRepo.countPending();
        BigDecimal passRate = inspectionRepo.passRateThisMonth();
        if (passRate == null) passRate = BigDecimal.ZERO;

        // procurement
        long openPOs = poRepo.countOpen();
        long overduePOs = poRepo.countOverdue(LocalDate.now());

        // sales
        long openOrders = salesRepo.countOpen();
        long dispatched = salesRepo.countDispatchedThisMonth();

        // alerts
        long critical = alertRepo.countBySeverityOpen("CRITICAL");
        long high = alertRepo.countBySeverityOpen("HIGH");
        long medium = alertRepo.countBySeverityOpen("MEDIUM");
        long low = alertRepo.countBySeverityOpen("LOW");

        return new DashboardKpiDto(
                new DashboardKpiDto.InventoryKpi(itemsWithStock, totalValue),
                new DashboardKpiDto.ProductionKpi(ordersInProgress, planned, good, yieldPct, target, attainmentPct),
                new DashboardKpiDto.QualityKpi(pending, passRate),
                new DashboardKpiDto.ProcurementKpi(openPOs, overduePOs),
                new DashboardKpiDto.SalesKpi(openOrders, dispatched),
                new DashboardKpiDto.AlertSummary(critical, high, medium, low, critical + high + medium + low));
    }
}

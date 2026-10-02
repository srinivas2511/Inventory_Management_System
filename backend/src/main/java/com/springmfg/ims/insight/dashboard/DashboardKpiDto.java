package com.springmfg.ims.insight.dashboard;

import java.math.BigDecimal;

public record DashboardKpiDto(
        InventoryKpi inventory,
        ProductionKpi production,
        QualityKpi quality,
        ProcurementKpi procurement,
        SalesKpi sales,
        AlertSummary alerts) {

    public record InventoryKpi(long itemsWithStock, BigDecimal totalValueInr) {}

    public record ProductionKpi(long ordersInProgress, BigDecimal plannedQtyMonth,
                                BigDecimal goodQtyMonth, BigDecimal yieldPct) {}

    public record QualityKpi(long pendingInspections, BigDecimal passRateMonth) {}

    public record ProcurementKpi(long openPOs, long overduePOs) {}

    public record SalesKpi(long openOrders, long dispatchedThisMonth) {}

    public record AlertSummary(long critical, long high, long medium, long low, long total) {}
}

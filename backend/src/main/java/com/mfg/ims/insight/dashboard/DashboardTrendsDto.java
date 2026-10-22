package com.mfg.ims.insight.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Monthly series behind the dashboard charts (UAT INS-01, INS-02), oldest month first. */
public record DashboardTrendsDto(List<ProductionMonth> production, List<InventoryValueMonth> inventoryValue) {

    /** Good and rejected output of the production orders created in the month. */
    public record ProductionMonth(LocalDate month, BigDecimal goodQty, BigDecimal rejectedQty) {}

    /** Inventory value at the end of the month: the running total of the ledger's value in less value out. */
    public record InventoryValueMonth(LocalDate month, BigDecimal closingValue) {}
}

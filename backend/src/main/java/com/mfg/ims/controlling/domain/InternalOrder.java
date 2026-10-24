package com.mfg.ims.controlling.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "internal_orders", schema = "ims")
public class InternalOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType = "CAPEX";

    @Column(name = "responsible_cost_centre_id")
    private Long responsibleCostCentreId;

    @Column(name = "budget_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal budgetAmount = BigDecimal.ZERO;

    @Column(name = "actual_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "settlement_cost_centre_id")
    private Long settlementCostCentreId;

    @Column(name = "settled_amount", precision = 18, scale = 4)
    private BigDecimal settledAmount;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by")
    private Long settledBy;

    @Column(name = "settlement_journal_id")
    private Long settlementJournalId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public String getOrderNumber() { return orderNumber; }
    public String getDescription() { return description; }
    public String getOrderType() { return orderType; }
    public Long getResponsibleCostCentreId() { return responsibleCostCentreId; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public String getStatus() { return status; }
    public Long getSettlementCostCentreId() { return settlementCostCentreId; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public Instant getSettledAt() { return settledAt; }
    public Long getSettledBy() { return settledBy; }
    public Long getSettlementJournalId() { return settlementJournalId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public void setOrderNumber(String v) { this.orderNumber = v; }
    public void setDescription(String v) { this.description = v; }
    public void setOrderType(String v) { this.orderType = v; }
    public void setResponsibleCostCentreId(Long v) { this.responsibleCostCentreId = v; }
    public void setBudgetAmount(BigDecimal v) { this.budgetAmount = v; }
    public void setActualAmount(BigDecimal v) { this.actualAmount = v; }
    public void setStatus(String v) { this.status = v; }
    public void setSettlementCostCentreId(Long v) { this.settlementCostCentreId = v; }
    public void setSettledAmount(BigDecimal v) { this.settledAmount = v; }
    public void setSettledAt(Instant v) { this.settledAt = v; }
    public void setSettledBy(Long v) { this.settledBy = v; }
    public void setSettlementJournalId(Long v) { this.settlementJournalId = v; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
}

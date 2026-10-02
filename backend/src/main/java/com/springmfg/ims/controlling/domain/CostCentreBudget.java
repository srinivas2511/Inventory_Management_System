package com.springmfg.ims.controlling.domain;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cost_centre_budgets", schema = "ims")
public class CostCentreBudget extends BaseEntity {

    @Column(name = "cost_centre_id", nullable = false)
    private Long costCentreId;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "budgeted_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal budgetedAmount;

    public Long getCostCentreId() { return costCentreId; }
    public Long getGlAccountId() { return glAccountId; }
    public Long getPeriodId() { return periodId; }
    public BigDecimal getBudgetedAmount() { return budgetedAmount; }

    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public void setGlAccountId(Long v) { this.glAccountId = v; }
    public void setPeriodId(Long v) { this.periodId = v; }
    public void setBudgetedAmount(BigDecimal v) { this.budgetedAmount = v; }
}

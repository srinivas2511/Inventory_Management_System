package com.springmfg.ims.finance.config.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "budgets", schema = "ims")
public class Budget extends BaseEntity {

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "budgeted_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal budgetedAmount;

    @Column(name = "budget_name", nullable = false, length = 100)
    private String budgetName;

    public Long getGlAccountId() { return glAccountId; }
    public Integer getFiscalYear() { return fiscalYear; }
    public Long getPeriodId() { return periodId; }
    public BigDecimal getBudgetedAmount() { return budgetedAmount; }
    public String getBudgetName() { return budgetName; }

    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setBudgetedAmount(BigDecimal budgetedAmount) { this.budgetedAmount = budgetedAmount; }
    public void setBudgetName(String budgetName) { this.budgetName = budgetName; }
}

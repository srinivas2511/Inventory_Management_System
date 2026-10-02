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

    @Column(name = "period_number", nullable = false)
    private Short periodNumber;

    @Column(name = "budgeted_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal budgetedAmount;

    @Column(name = "cost_center", length = 20)
    private String costCenter;

    public Long getGlAccountId() { return glAccountId; }
    public Integer getFiscalYear() { return fiscalYear; }
    public Short getPeriodNumber() { return periodNumber; }
    public BigDecimal getBudgetedAmount() { return budgetedAmount; }
    public String getCostCenter() { return costCenter; }

    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public void setPeriodNumber(Short periodNumber) { this.periodNumber = periodNumber; }
    public void setBudgetedAmount(BigDecimal budgetedAmount) { this.budgetedAmount = budgetedAmount; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
}

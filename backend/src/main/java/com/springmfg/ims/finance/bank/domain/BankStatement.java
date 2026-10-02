package com.springmfg.ims.finance.bank.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_statements", schema = "ims")
public class BankStatement extends BaseEntity {

    @Column(name = "statement_number", nullable = false, unique = true, length = 25)
    private String statementNumber;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "opening_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "closing_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    private String status = "OPEN";

    public String getStatementNumber() { return statementNumber; }
    public Long getGlAccountId() { return glAccountId; }
    public LocalDate getStatementDate() { return statementDate; }
    public Long getPeriodId() { return periodId; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public String getStatus() { return status; }

    public void setStatementNumber(String statementNumber) { this.statementNumber = statementNumber; }
    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
    public void setStatus(String status) { this.status = status; }
}

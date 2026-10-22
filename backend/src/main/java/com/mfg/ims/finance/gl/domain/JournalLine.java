package com.mfg.ims.finance.gl.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_lines", schema = "ims")
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_header_id", nullable = false)
    private JournalHeader journalHeader;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "debit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(length = 200)
    private String description;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "profit_centre_id")
    private Long profitCentreId;

    public Long getId() { return id; }
    public Long getCostCentreId() { return costCentreId; }
    public Long getProfitCentreId() { return profitCentreId; }
    public JournalHeader getJournalHeader() { return journalHeader; }
    public Integer getLineNumber() { return lineNumber; }
    public Long getGlAccountId() { return glAccountId; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getDescription() { return description; }

    public void setJournalHeader(JournalHeader journalHeader) { this.journalHeader = journalHeader; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public void setGlAccountId(Long glAccountId) { this.glAccountId = glAccountId; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public void setDescription(String description) { this.description = description; }
    public void setCostCentreId(Long costCentreId) { this.costCentreId = costCentreId; }
    public void setProfitCentreId(Long profitCentreId) { this.profitCentreId = profitCentreId; }
}

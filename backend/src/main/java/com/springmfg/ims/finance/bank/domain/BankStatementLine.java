package com.springmfg.ims.finance.bank.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_statement_lines", schema = "ims")
public class BankStatementLine extends BaseEntity {

    @Column(name = "bank_statement_id", nullable = false)
    private Long bankStatementId;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(length = 200)
    private String description;

    @Column(name = "debit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "bank_reference", length = 60)
    private String referenceNumber;

    @Column(name = "matched_journal_line_id")
    private Long matchedJournalLineId;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    public Long getBankStatementId() { return bankStatementId; }
    public LocalDate getValueDate() { return valueDate; }
    public String getDescription() { return description; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getReferenceNumber() { return referenceNumber; }
    public Long getMatchedJournalLineId() { return matchedJournalLineId; }
    public Integer getLineNumber() { return lineNumber; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public boolean isReconciled() { return reconciled; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }

    /** Derived from DESIGN columns: UNMATCHED (not reconciled), MATCHED (linked to a journal line), AUTO_MATCHED. */
    public String getMatchStatus() {
        if (!reconciled) return "UNMATCHED";
        return matchedJournalLineId != null ? "MATCHED" : "AUTO_MATCHED";
    }

    public void setBankStatementId(Long bankStatementId) { this.bankStatementId = bankStatementId; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public void setDescription(String description) { this.description = description; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public void setMatchedJournalLineId(Long matchedJournalLineId) { this.matchedJournalLineId = matchedJournalLineId; }
    public void setMatchStatus(String matchStatus) { this.reconciled = !"UNMATCHED".equals(matchStatus); }
}

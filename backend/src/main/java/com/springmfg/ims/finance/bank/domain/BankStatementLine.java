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

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(length = 200)
    private String description;

    @Column(name = "debit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "reference_number", length = 40)
    private String referenceNumber;

    @Column(name = "matched_journal_line_id")
    private Long matchedJournalLineId;

    @Column(name = "match_status", nullable = false, length = 15)
    private String matchStatus = "UNMATCHED";

    public Long getBankStatementId() { return bankStatementId; }
    public LocalDate getValueDate() { return valueDate; }
    public String getDescription() { return description; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public String getReferenceNumber() { return referenceNumber; }
    public Long getMatchedJournalLineId() { return matchedJournalLineId; }
    public String getMatchStatus() { return matchStatus; }

    public void setBankStatementId(Long bankStatementId) { this.bankStatementId = bankStatementId; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public void setDescription(String description) { this.description = description; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public void setMatchedJournalLineId(Long matchedJournalLineId) { this.matchedJournalLineId = matchedJournalLineId; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
}

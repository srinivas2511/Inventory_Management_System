package com.mfg.ims.finance.gl.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_headers", schema = "ims")
public class JournalHeader extends BaseEntity {

    @Column(name = "journal_number", nullable = false, unique = true, length = 20)
    private String journalNumber;

    @Column(name = "journal_type", nullable = false, length = 20)
    private String journalType;

    @Column(name = "source_module", length = 20)
    private String sourceModule;

    @Column(name = "source_document_type", length = 30)
    private String sourceDocumentType;

    @Column(name = "source_document_id")
    private Long sourceDocumentId;

    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "prepared_by", nullable = false)
    private Long preparedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @OneToMany(mappedBy = "journalHeader", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<JournalLine> lines = new ArrayList<>();

    public String getJournalNumber() { return journalNumber; }
    public String getJournalType() { return journalType; }
    public String getSourceModule() { return sourceModule; }
    public String getSourceDocumentType() { return sourceDocumentType; }
    public Long getSourceDocumentId() { return sourceDocumentId; }
    public LocalDate getPostingDate() { return postingDate; }
    public Long getPeriodId() { return periodId; }
    public String getDescription() { return description; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public String getStatus() { return status; }
    public Long getReversalOfId() { return reversalOfId; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Long getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public List<JournalLine> getLines() { return lines; }

    public void setJournalNumber(String journalNumber) { this.journalNumber = journalNumber; }
    public void setJournalType(String journalType) { this.journalType = journalType; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }
    public void setSourceDocumentType(String sourceDocumentType) { this.sourceDocumentType = sourceDocumentType; }
    public void setSourceDocumentId(Long sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }
    public void setPostingDate(LocalDate postingDate) { this.postingDate = postingDate; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setDescription(String description) { this.description = description; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public void setStatus(String status) { this.status = status; }
    public void setReversalOfId(Long reversalOfId) { this.reversalOfId = reversalOfId; }
    public void setPreparedBy(Long preparedBy) { this.preparedBy = preparedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setPostedBy(Long postedBy) { this.postedBy = postedBy; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public void setLines(List<JournalLine> lines) { this.lines = lines; }
}

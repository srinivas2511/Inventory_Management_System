package com.mfg.ims.finance.fx.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "fx_revaluations", schema = "ims")
public class FxRevaluation extends BaseEntity {

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "document_type", nullable = false, length = 15)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "remaining_foreign", nullable = false, precision = 18, scale = 4)
    private BigDecimal remainingForeign;

    @Column(name = "booking_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal bookingRate;

    @Column(name = "closing_rate", nullable = false, precision = 12, scale = 6)
    private BigDecimal closingRate;

    @Column(name = "difference_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal differenceInr;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    public Long getPeriodId() { return periodId; }
    public String getDocumentType() { return documentType; }
    public Long getDocumentId() { return documentId; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getRemainingForeign() { return remainingForeign; }
    public BigDecimal getBookingRate() { return bookingRate; }
    public BigDecimal getClosingRate() { return closingRate; }
    public BigDecimal getDifferenceInr() { return differenceInr; }
    public Long getJournalHeaderId() { return journalHeaderId; }

    public void setPeriodId(Long periodId) { this.periodId = periodId; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setRemainingForeign(BigDecimal remainingForeign) { this.remainingForeign = remainingForeign; }
    public void setBookingRate(BigDecimal bookingRate) { this.bookingRate = bookingRate; }
    public void setClosingRate(BigDecimal closingRate) { this.closingRate = closingRate; }
    public void setDifferenceInr(BigDecimal differenceInr) { this.differenceInr = differenceInr; }
    public void setJournalHeaderId(Long journalHeaderId) { this.journalHeaderId = journalHeaderId; }
}

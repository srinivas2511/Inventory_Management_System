package com.mfg.ims.finance.payment;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments", schema = "ims")
public class Payment extends BaseEntity {

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "status", nullable = false, length = 18)
    private String status = "DRAFT";

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind = "REGULAR";

    @Column(name = "party_type", nullable = false, length = 10)
    private String partyType;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "mode", nullable = false, length = 12)
    private String mode;

    @Column(name = "reference_no", length = 60)
    private String referenceNo;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "amount_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountInr;

    @Column(name = "tds_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "bank_charges", nullable = false, precision = 18, scale = 4)
    private BigDecimal bankCharges = BigDecimal.ZERO;

    @Column(name = "unallocated_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal unallocatedInr = BigDecimal.ZERO;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAllocation> allocations = new ArrayList<>();

    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { this.direction = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String v) { this.partyType = v; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long v) { this.partyId = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { this.referenceNo = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal v) { this.exchangeRate = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public BigDecimal getAmountInr() { return amountInr; }
    public void setAmountInr(BigDecimal v) { this.amountInr = v; }
    public BigDecimal getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(BigDecimal v) { this.tdsAmount = v; }
    public BigDecimal getBankCharges() { return bankCharges; }
    public void setBankCharges(BigDecimal v) { this.bankCharges = v; }
    public BigDecimal getUnallocatedInr() { return unallocatedInr; }
    public void setUnallocatedInr(BigDecimal v) { this.unallocatedInr = v; }
    public Long getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Long v) { this.journalEntryId = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public List<PaymentAllocation> getAllocations() { return allocations; }
}

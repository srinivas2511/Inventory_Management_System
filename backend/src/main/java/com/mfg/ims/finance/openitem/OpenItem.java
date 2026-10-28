package com.mfg.ims.finance.openitem;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "open_items", schema = "ims")
public class OpenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "item_type", nullable = false, length = 25)
    private String itemType;

    @Column(name = "party_type", nullable = false, length = 10)
    private String partyType;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "doc_no", length = 40)
    private String docNo;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "amount_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountInr;

    @Column(name = "balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal balance;

    @Column(name = "balance_inr", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceInr;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String v) { this.partyType = v; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long v) { this.partyId = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { this.sourceType = v; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long v) { this.sourceId = v; }
    public String getDocNo() { return docNo; }
    public void setDocNo(String v) { this.docNo = v; }
    public LocalDate getDocDate() { return docDate; }
    public void setDocDate(LocalDate v) { this.docDate = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public BigDecimal getAmountInr() { return amountInr; }
    public void setAmountInr(BigDecimal v) { this.amountInr = v; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal v) { this.balance = v; }
    public BigDecimal getBalanceInr() { return balanceInr; }
    public void setBalanceInr(BigDecimal v) { this.balanceInr = v; }
}

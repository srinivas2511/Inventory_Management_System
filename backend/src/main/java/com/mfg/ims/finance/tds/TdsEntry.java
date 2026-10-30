package com.mfg.ims.finance.tds;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single TDS (Tax Deducted at Source) entry linked to a TDS section.
 * Mapped to the {@code ims.tds_entries} table created in V68.
 */
@Entity
@Table(name = "tds_entries", schema = "ims")
public class TdsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    /** DEDUCTED (as payer) or COLLECTED (as payee). */
    @Column(name = "direction", length = 10)
    private String direction;

    @Column(name = "party_type", length = 10)
    private String partyType;

    @Column(name = "party_id")
    private Long partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private TdsSection section;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "base_amount", precision = 18, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "rate", precision = 9, scale = 4)
    private BigDecimal rate;

    @Column(name = "tds_amount", precision = 18, scale = 4)
    private BigDecimal tdsAmount;

    @Column(name = "deduction_date")
    private LocalDate deductionDate;

    @Column(name = "challan_no", length = 30)
    private String challanNo;

    @Column(name = "fiscal_year", length = 9)
    private String fiscalYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    // ----- getters -----

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { this.direction = v; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String v) { this.partyType = v; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long v) { this.partyId = v; }
    public TdsSection getSection() { return section; }
    public void setSection(TdsSection v) { this.section = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { this.sourceType = v; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long v) { this.sourceId = v; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal v) { this.baseAmount = v; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal v) { this.rate = v; }
    public BigDecimal getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(BigDecimal v) { this.tdsAmount = v; }
    public LocalDate getDeductionDate() { return deductionDate; }
    public void setDeductionDate(LocalDate v) { this.deductionDate = v; }
    public String getChallanNo() { return challanNo; }
    public void setChallanNo(String v) { this.challanNo = v; }
    public String getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(String v) { this.fiscalYear = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long v) { this.createdBy = v; }
}

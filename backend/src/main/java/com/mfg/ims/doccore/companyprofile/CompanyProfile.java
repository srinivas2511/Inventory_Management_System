package com.mfg.ims.doccore.companyprofile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "company_profile", schema = "ims")
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId = 1L;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "cin", length = 25)
    private String cin;

    @Column(name = "logo_storage_key", length = 200)
    private String logoStorageKey;

    @Column(name = "default_payment_terms_id")
    private Long defaultPaymentTermsId;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth = 4;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public CompanyProfile() {}

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String v) { this.legalName = v; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String v) { this.tradeName = v; }
    public String getPan() { return pan; }
    public void setPan(String v) { this.pan = v; }
    public String getCin() { return cin; }
    public void setCin(String v) { this.cin = v; }
    public String getLogoStorageKey() { return logoStorageKey; }
    public void setLogoStorageKey(String v) { this.logoStorageKey = v; }
    public Long getDefaultPaymentTermsId() { return defaultPaymentTermsId; }
    public void setDefaultPaymentTermsId(Long v) { this.defaultPaymentTermsId = v; }
    public int getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public void setFiscalYearStartMonth(int v) { this.fiscalYearStartMonth = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { this.website = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long v) { this.updatedBy = v; }
}

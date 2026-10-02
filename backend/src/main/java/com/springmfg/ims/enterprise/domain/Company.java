package com.springmfg.ims.enterprise.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "companies", schema = "ims")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, unique = true, length = 10)
    private String companyCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 20)
    private String gstin;

    @Column(length = 15)
    private String pan;

    @Column(name = "registered_address")
    private String registeredAddress;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 4;

    @Column(nullable = false)
    private Boolean active = true;

    public Long getId() { return id; }
    public String getCompanyCode() { return companyCode; }
    public String getName() { return name; }
    public String getLegalName() { return legalName; }
    public String getGstin() { return gstin; }
    public String getPan() { return pan; }
    public String getRegisteredAddress() { return registeredAddress; }
    public String getCurrencyCode() { return currencyCode; }
    public Integer getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public Boolean getActive() { return active; }

    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
    public void setName(String name) { this.name = name; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public void setPan(String pan) { this.pan = pan; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setFiscalYearStartMonth(Integer fiscalYearStartMonth) { this.fiscalYearStartMonth = fiscalYearStartMonth; }
    public void setActive(Boolean active) { this.active = active; }
}

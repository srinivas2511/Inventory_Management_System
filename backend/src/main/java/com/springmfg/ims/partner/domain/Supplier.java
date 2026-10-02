package com.springmfg.ims.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "suppliers", schema = "ims")
public class Supplier extends BaseEntity {

    @Column(name = "supplier_code", nullable = false, unique = true, length = 20)
    private String supplierCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(length = 20)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(length = 400)
    private String address;

    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    @Column(name = "payment_terms", length = 60)
    private String paymentTerms;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    public Supplier() {}

    // ── getters ──────────────────────────────────────────────────────────────
    public String getSupplierCode() { return supplierCode; }
    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getGstNumber() { return gstNumber; }
    public String getPaymentTerms() { return paymentTerms; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public String getStatus() { return status; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }
    public void setName(String name) { this.name = name; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public void setStatus(String status) { this.status = status; }
}

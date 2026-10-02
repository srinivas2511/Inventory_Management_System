package com.springmfg.ims.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "customers", schema = "ims")
public class Customer extends BaseEntity {

    @Column(name = "customer_code", nullable = false, unique = true, length = 20)
    private String customerCode;

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

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    public Customer() {}

    // ── getters ──────────────────────────────────────────────────────────────
    public String getCustomerCode() { return customerCode; }
    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getGstNumber() { return gstNumber; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getStatus() { return status; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public void setName(String name) { this.name = name; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public void setStatus(String status) { this.status = status; }
}

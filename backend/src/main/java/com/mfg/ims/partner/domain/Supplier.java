package com.mfg.ims.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

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

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(name = "state_name", length = 100)
    private String stateName;

    @Column(name = "pin_code", length = 10)
    private String pinCode;

    @Column(length = 100)
    private String country;

    @Column(length = 10)
    private String pan;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    @Column(name = "payment_terms", length = 60)
    private String paymentTerms;

    @Column(name = "payment_terms_id")
    private Long paymentTermsId;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "vendor_type_id")
    private Long vendorTypeId;

    public Supplier() {}

    // ── getters ──────────────────────────────────────────────────────────────
    public String getSupplierCode() { return supplierCode; }
    public String getName() { return name; }
    public String getContactPerson() { return contactPerson; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getStateName() { return stateName; }
    public String getPinCode() { return pinCode; }
    public String getCountry() { return country; }
    public String getPan() { return pan; }
    public String getCurrencyCode() { return currencyCode; }
    public String getGstNumber() { return gstNumber; }
    public String getPaymentTerms() { return paymentTerms; }
    public Long getPaymentTermsId() { return paymentTermsId; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public String getStatus() { return status; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }
    public void setName(String name) { this.name = name; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
    public void setAddressLine1(String v) { this.addressLine1 = v; }
    public void setAddressLine2(String v) { this.addressLine2 = v; }
    public void setCity(String v) { this.city = v; }
    public void setStateName(String v) { this.stateName = v; }
    public void setPinCode(String v) { this.pinCode = v; }
    public void setCountry(String v) { this.country = v; }
    public void setPan(String v) { this.pan = v; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public void setPaymentTermsId(Long v) { this.paymentTermsId = v; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }

    public Long getVendorTypeId() { return vendorTypeId; }
    public void setVendorTypeId(Long v) { this.vendorTypeId = v; }
}

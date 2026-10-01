package com.springmfg.ims.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import com.springmfg.ims.common.domain.BaseEntity;

/**
 * Fields shared by suppliers and customers. Each is deactivated, never deleted; deactivation only stops <em>new</em>
 * documents (DESIGN.md section 5.2). The {@code status} column mirrors {@code active} for the documented schema.
 */
@MappedSuperclass
public abstract class BusinessPartner extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "gst_number")
    private String gstNumber;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** The business code ({@code supplier_code} or {@code customer_code}); never changes after creation. */
    public abstract String getCode();

    public void update(String name, String contactPerson, String phone, String email, String address, String gstNumber,
            String paymentTerms, Integer leadTimeDays) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.gstNumber = gstNumber;
        this.paymentTerms = paymentTerms;
        this.leadTimeDays = leadTimeDays;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? "ACTIVE" : "INACTIVE";
    }

    public String getName() {
        return name;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public String getStatus() {
        return status;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public boolean isActive() {
        return active;
    }
}

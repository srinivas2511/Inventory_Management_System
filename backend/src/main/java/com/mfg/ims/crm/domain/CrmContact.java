package com.mfg.ims.crm.domain;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_contacts", schema = "ims")
public class CrmContact extends BaseEntity {

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String designation;

    @Column(length = 60)
    private String department;

    @Column(nullable = false)
    private boolean active = true;

    public Long getCustomerId() { return customerId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getDesignation() { return designation; }
    public String getDepartment() { return department; }
    public boolean isActive() { return active; }

    public void setCustomerId(Long v) { this.customerId = v; }
    public void setFirstName(String v) { this.firstName = v; }
    public void setLastName(String v) { this.lastName = v; }
    public void setEmail(String v) { this.email = v; }
    public void setPhone(String v) { this.phone = v; }
    public void setDesignation(String v) { this.designation = v; }
    public void setDepartment(String v) { this.department = v; }
    public void setActive(boolean v) { this.active = v; }

    public String fullName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }
}

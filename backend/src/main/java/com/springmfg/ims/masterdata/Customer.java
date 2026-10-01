package com.springmfg.ims.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends BusinessPartner {

    @Column(name = "customer_code", nullable = false, updatable = false)
    private String customerCode;

    protected Customer() {
    }

    public Customer(String customerCode) {
        this.customerCode = customerCode;
    }

    @Override
    public String getCode() {
        return customerCode;
    }

    public String getCustomerCode() {
        return customerCode;
    }
}

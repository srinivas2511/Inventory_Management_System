package com.springmfg.ims.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier extends BusinessPartner {

    @Column(name = "supplier_code", nullable = false, updatable = false)
    private String supplierCode;

    protected Supplier() {
    }

    public Supplier(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    @Override
    public String getCode() {
        return supplierCode;
    }

    public String getSupplierCode() {
        return supplierCode;
    }
}

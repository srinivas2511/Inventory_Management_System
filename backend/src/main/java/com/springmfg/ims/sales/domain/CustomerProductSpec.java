package com.springmfg.ims.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "customer_product_specs", schema = "ims")
public class CustomerProductSpec extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "spec_reference", length = 100)
    private String specReference;

    @Column(name = "drawing_number", length = 50)
    private String drawingNumber;

    @Column(name = "customer_part_number", length = 50)
    private String customerPartNumber;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public CustomerProductSpec() {}

    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
    public String getSpecReference() { return specReference; }
    public String getDrawingNumber() { return drawingNumber; }
    public String getCustomerPartNumber() { return customerPartNumber; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setSpecReference(String specReference) { this.specReference = specReference; }
    public void setDrawingNumber(String drawingNumber) { this.drawingNumber = drawingNumber; }
    public void setCustomerPartNumber(String customerPartNumber) { this.customerPartNumber = customerPartNumber; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setActive(boolean active) { this.active = active; }
}

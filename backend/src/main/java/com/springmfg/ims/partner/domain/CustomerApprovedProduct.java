package com.springmfg.ims.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

/** MDM-021: a product a customer is approved to buy. */
@Entity
@Table(name = "customer_approved_products", schema = "ims")
public class CustomerApprovedProduct extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    public CustomerApprovedProduct() {}

    public CustomerApprovedProduct(Long customerId, Long productId) {
        this.customerId = customerId;
        this.productId = productId;
    }

    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
}

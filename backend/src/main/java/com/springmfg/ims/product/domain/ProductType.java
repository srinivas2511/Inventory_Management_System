package com.springmfg.ims.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

/** A product type code (spring types and any vertical's types); products.spring_type references it. */
@Entity
@Table(name = "product_types", schema = "ims")
public class ProductType extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 20)
    private String industry;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected ProductType() {
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getIndustry() { return industry; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}

package com.springmfg.ims.finance.tax.domain;

import com.springmfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** The GST slab a product is sold under. A product without a row is invoiced without tax. */
@Entity
@Table(name = "product_tax_slabs", schema = "ims")
public class ProductTaxSlab extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "slab_code", nullable = false, length = 15)
    private String slabCode;

    public Long getProductId() { return productId; }
    public String getSlabCode() { return slabCode; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setSlabCode(String slabCode) { this.slabCode = slabCode; }
}

package com.mfg.ims.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

/** One raw material linked to a product (V117). */
@Entity
@Table(name = "product_materials", schema = "ims")
public class ProductMaterial extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public ProductMaterial() {}

    public ProductMaterial(Long productId, Long materialId) {
        this.productId = productId;
        this.materialId = materialId;
    }

    public Long getProductId() { return productId; }
    public Long getMaterialId() { return materialId; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}

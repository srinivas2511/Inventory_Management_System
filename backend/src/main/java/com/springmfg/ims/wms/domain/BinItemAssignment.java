package com.springmfg.ims.wms.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.springmfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "bin_item_assignments", schema = "ims")
public class BinItemAssignment extends BaseEntity {

    @Column(name = "bin_id", nullable = false)
    private Long binId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    public BinItemAssignment() {
    }

    public Long getBinId() { return binId; }
    public void setBinId(Long binId) { this.binId = binId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}

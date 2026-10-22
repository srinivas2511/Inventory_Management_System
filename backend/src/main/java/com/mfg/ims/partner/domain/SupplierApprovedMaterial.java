package com.mfg.ims.partner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

/** MDM-020: a material a supplier is approved to supply. */
@Entity
@Table(name = "supplier_approved_materials", schema = "ims")
public class SupplierApprovedMaterial extends BaseEntity {

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    public SupplierApprovedMaterial() {}

    public SupplierApprovedMaterial(Long supplierId, Long materialId) {
        this.supplierId = supplierId;
        this.materialId = materialId;
    }

    public Long getSupplierId() { return supplierId; }
    public Long getMaterialId() { return materialId; }
}

package com.mfg.ims.maintenance.domain;

import java.math.BigDecimal;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A maintenance spare part. Its stock is the stock of {@code materialId}, so every movement goes through the ledger. */
@Entity
@Table(name = "spare_parts", schema = "ims")
public class SparePart extends BaseEntity {

    @Column(name = "spare_part_code", nullable = false, unique = true, length = 20)
    private String sparePartCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "uom", nullable = false, length = 10)
    private String uom;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "compatible_machine_types", length = 200)
    private String compatibleMachineTypes;

    @Column(name = "min_stock", nullable = false, precision = 18, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "standard_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getSparePartCode() { return sparePartCode; }
    public void setSparePartCode(String v) { this.sparePartCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getUom() { return uom; }
    public void setUom(String v) { this.uom = v; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public String getCompatibleMachineTypes() { return compatibleMachineTypes; }
    public void setCompatibleMachineTypes(String v) { this.compatibleMachineTypes = v; }
    public BigDecimal getMinStock() { return minStock; }
    public void setMinStock(BigDecimal v) { this.minStock = v; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal v) { this.reorderLevel = v; }
    public BigDecimal getStandardCost() { return standardCost; }
    public void setStandardCost(BigDecimal v) { this.standardCost = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}

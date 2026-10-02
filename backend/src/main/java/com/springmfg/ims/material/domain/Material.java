package com.springmfg.ims.material.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;
import com.springmfg.ims.partner.domain.Supplier;

@Entity
@Table(name = "materials", schema = "ims")
public class Material extends BaseEntity {

    @Column(name = "material_code", nullable = false, unique = true, length = 30)
    private String materialCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "material_type", nullable = false, length = 40)
    private String materialType;

    @Column(length = 30)
    private String grade;

    @Column(name = "diameter_mm", precision = 10, scale = 3)
    private BigDecimal diameterMm;

    @Column(nullable = false, length = 10)
    private String uom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private Supplier preferredSupplier;

    @Column(name = "min_stock", nullable = false, precision = 18, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 18, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "max_stock", precision = 18, scale = 3)
    private BigDecimal maxStock;

    @Column(name = "standard_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public Material() {}

    // ── getters ──────────────────────────────────────────────────────────────
    public String getMaterialCode() { return materialCode; }
    public String getName() { return name; }
    public String getMaterialType() { return materialType; }
    public String getGrade() { return grade; }
    public BigDecimal getDiameterMm() { return diameterMm; }
    public String getUom() { return uom; }
    public Supplier getPreferredSupplier() { return preferredSupplier; }
    public BigDecimal getMinStock() { return minStock; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public BigDecimal getMaxStock() { return maxStock; }
    public BigDecimal getStandardCost() { return standardCost; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public void setName(String name) { this.name = name; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    public void setGrade(String grade) { this.grade = grade; }
    public void setDiameterMm(BigDecimal diameterMm) { this.diameterMm = diameterMm; }
    public void setUom(String uom) { this.uom = uom; }
    public void setPreferredSupplier(Supplier preferredSupplier) { this.preferredSupplier = preferredSupplier; }
    public void setMinStock(BigDecimal minStock) { this.minStock = minStock; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public void setMaxStock(BigDecimal maxStock) { this.maxStock = maxStock; }
    public void setStandardCost(BigDecimal standardCost) { this.standardCost = standardCost; }
    public void setShelfLifeDays(Integer shelfLifeDays) { this.shelfLifeDays = shelfLifeDays; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.active = active; }
}

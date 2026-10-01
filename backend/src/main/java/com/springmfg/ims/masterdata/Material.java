package com.springmfg.ims.masterdata;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;

/** A raw material or consumable (DESIGN.md section 2.3). Deactivated, never deleted. */
@Entity
@Table(name = "materials")
public class Material extends BaseEntity {

    @Column(name = "material_code", nullable = false, updatable = false)
    private String materialCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false)
    private MaterialType materialType;

    @Column(name = "grade")
    private String grade;

    @Column(name = "diameter_mm")
    private BigDecimal diameterMm;

    @Column(name = "uom", nullable = false)
    private String uom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supplier_id")
    private Supplier preferredSupplier;

    @Column(name = "min_stock", nullable = false)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "max_stock")
    private BigDecimal maxStock;

    @Column(name = "standard_cost", nullable = false)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Material() {
    }

    public Material(String materialCode) {
        this.materialCode = materialCode;
    }

    public void update(String name, MaterialType materialType, String grade, BigDecimal diameterMm, String uom,
            Supplier preferredSupplier, BigDecimal minStock, BigDecimal reorderLevel, BigDecimal maxStock,
            BigDecimal standardCost, Integer shelfLifeDays, String description) {
        this.name = name;
        this.materialType = materialType;
        this.grade = grade;
        this.diameterMm = diameterMm;
        this.uom = uom;
        this.preferredSupplier = preferredSupplier;
        this.minStock = minStock;
        this.reorderLevel = reorderLevel;
        this.maxStock = maxStock;
        this.standardCost = standardCost;
        this.shelfLifeDays = shelfLifeDays;
        this.description = description;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public String getName() {
        return name;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public String getGrade() {
        return grade;
    }

    public BigDecimal getDiameterMm() {
        return diameterMm;
    }

    public String getUom() {
        return uom;
    }

    public Supplier getPreferredSupplier() {
        return preferredSupplier;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public BigDecimal getStandardCost() {
        return standardCost;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}

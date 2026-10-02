package com.springmfg.ims.product.domain;

import java.math.BigDecimal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.springmfg.ims.common.domain.BaseEntity;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.partner.domain.Customer;

@Entity
@Table(name = "products", schema = "ims")
public class Product extends BaseEntity {

    @Column(name = "product_code", nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "spring_type", nullable = false, length = 20)
    private String springType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_material_id")
    private Material primaryMaterial;

    @Column(name = "wire_diameter", precision = 10, scale = 3)
    private BigDecimal wireDiameter;

    @Column(name = "outer_diameter", precision = 10, scale = 3)
    private BigDecimal outerDiameter;

    @Column(name = "inner_diameter", precision = 10, scale = 3)
    private BigDecimal innerDiameter;

    @Column(name = "free_length", precision = 10, scale = 3)
    private BigDecimal freeLength;

    @Column(name = "number_of_coils", precision = 8, scale = 2)
    private BigDecimal numberOfCoils;

    @Column(name = "active_coils", precision = 8, scale = 2)
    private BigDecimal activeCoils;

    @Column(name = "spring_rate", precision = 12, scale = 4)
    private BigDecimal springRate;

    @Column(name = "max_load", precision = 12, scale = 3)
    private BigDecimal maxLoad;

    @Column(name = "min_load", precision = 12, scale = 3)
    private BigDecimal minLoad;

    @Column(name = "working_length", precision = 10, scale = 3)
    private BigDecimal workingLength;

    @Column(name = "solid_height", precision = 10, scale = 3)
    private BigDecimal solidHeight;

    @Column(name = "end_type", length = 30)
    private String endType;

    @Column(name = "surface_treatment", length = 60)
    private String surfaceTreatment;

    @Column(name = "heat_treatment", length = 60)
    private String heatTreatment;

    @Column(length = 60)
    private String tolerance;

    @Column(name = "unit_weight_kg", precision = 12, scale = 6)
    private BigDecimal unitWeightKg;

    @Column(nullable = false, length = 10)
    private String uom = "PCS";

    @Column(name = "drawing_number", length = 40)
    private String drawingNumber;

    @Column(name = "drawing_revision", length = 10)
    private String drawingRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, length = 15)
    private String status = "DRAFT";

    @Column(name = "reorder_level", precision = 18, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "standard_cost", precision = 18, scale = 4)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private SpringSpecification specification;

    public Product() {}

    // ── getters ──────────────────────────────────────────────────────────────
    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getSpringType() { return springType; }
    public Material getPrimaryMaterial() { return primaryMaterial; }
    public BigDecimal getWireDiameter() { return wireDiameter; }
    public BigDecimal getOuterDiameter() { return outerDiameter; }
    public BigDecimal getInnerDiameter() { return innerDiameter; }
    public BigDecimal getFreeLength() { return freeLength; }
    public BigDecimal getNumberOfCoils() { return numberOfCoils; }
    public BigDecimal getActiveCoils() { return activeCoils; }
    public BigDecimal getSpringRate() { return springRate; }
    public BigDecimal getMaxLoad() { return maxLoad; }
    public BigDecimal getMinLoad() { return minLoad; }
    public BigDecimal getWorkingLength() { return workingLength; }
    public BigDecimal getSolidHeight() { return solidHeight; }
    public String getEndType() { return endType; }
    public String getSurfaceTreatment() { return surfaceTreatment; }
    public String getHeatTreatment() { return heatTreatment; }
    public String getTolerance() { return tolerance; }
    public BigDecimal getUnitWeightKg() { return unitWeightKg; }
    public String getUom() { return uom; }
    public String getDrawingNumber() { return drawingNumber; }
    public String getDrawingRevision() { return drawingRevision; }
    public Customer getCustomer() { return customer; }
    public String getStatus() { return status; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public BigDecimal getStandardCost() { return standardCost; }
    public boolean isActive() { return active; }
    public SpringSpecification getSpecification() { return specification; }

    // ── setters ──────────────────────────────────────────────────────────────
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setName(String name) { this.name = name; }
    public void setSpringType(String springType) { this.springType = springType; }
    public void setPrimaryMaterial(Material primaryMaterial) { this.primaryMaterial = primaryMaterial; }
    public void setWireDiameter(BigDecimal wireDiameter) { this.wireDiameter = wireDiameter; }
    public void setOuterDiameter(BigDecimal outerDiameter) { this.outerDiameter = outerDiameter; }
    public void setInnerDiameter(BigDecimal innerDiameter) { this.innerDiameter = innerDiameter; }
    public void setFreeLength(BigDecimal freeLength) { this.freeLength = freeLength; }
    public void setNumberOfCoils(BigDecimal numberOfCoils) { this.numberOfCoils = numberOfCoils; }
    public void setActiveCoils(BigDecimal activeCoils) { this.activeCoils = activeCoils; }
    public void setSpringRate(BigDecimal springRate) { this.springRate = springRate; }
    public void setMaxLoad(BigDecimal maxLoad) { this.maxLoad = maxLoad; }
    public void setMinLoad(BigDecimal minLoad) { this.minLoad = minLoad; }
    public void setWorkingLength(BigDecimal workingLength) { this.workingLength = workingLength; }
    public void setSolidHeight(BigDecimal solidHeight) { this.solidHeight = solidHeight; }
    public void setEndType(String endType) { this.endType = endType; }
    public void setSurfaceTreatment(String surfaceTreatment) { this.surfaceTreatment = surfaceTreatment; }
    public void setHeatTreatment(String heatTreatment) { this.heatTreatment = heatTreatment; }
    public void setTolerance(String tolerance) { this.tolerance = tolerance; }
    public void setUnitWeightKg(BigDecimal unitWeightKg) { this.unitWeightKg = unitWeightKg; }
    public void setUom(String uom) { this.uom = uom; }
    public void setDrawingNumber(String drawingNumber) { this.drawingNumber = drawingNumber; }
    public void setDrawingRevision(String drawingRevision) { this.drawingRevision = drawingRevision; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public void setStatus(String status) { this.status = status; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public void setStandardCost(BigDecimal standardCost) { this.standardCost = standardCost; }
    public void setActive(boolean active) { this.active = active; }
    public void setSpecification(SpringSpecification specification) { this.specification = specification; }
}

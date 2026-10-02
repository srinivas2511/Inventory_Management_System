package com.springmfg.ims.product;

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
import com.springmfg.ims.masterdata.Customer;
import com.springmfg.ims.masterdata.Material;

/**
 * A spring product (DESIGN.md section 2.3): the columns the whole system filters and reports on, plus (in
 * {@link SpringSpecification}) the attributes specific to its type. {@code active} is true unless the product is
 * obsolete, which keeps "in use" queries on products simple.
 */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    /** Everything an engineer edits, as one value so create and update share a single code path. */
    public record Details(String name, SpringType springType, Material primaryMaterial, BigDecimal wireDiameter,
            BigDecimal outerDiameter, BigDecimal innerDiameter, BigDecimal freeLength, BigDecimal numberOfCoils,
            BigDecimal activeCoils, BigDecimal springRate, BigDecimal maxLoad, BigDecimal minLoad,
            BigDecimal workingLength, BigDecimal solidHeight, String endType, String surfaceTreatment,
            String heatTreatment, String tolerance, BigDecimal unitWeightKg, String uom, String drawingNumber,
            String drawingRevision, Customer customer, BigDecimal reorderLevel, BigDecimal standardCost) {
    }

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "spring_type", nullable = false)
    private SpringType springType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_material_id")
    private Material primaryMaterial;

    @Column(name = "wire_diameter")
    private BigDecimal wireDiameter;

    @Column(name = "outer_diameter")
    private BigDecimal outerDiameter;

    @Column(name = "inner_diameter")
    private BigDecimal innerDiameter;

    @Column(name = "free_length")
    private BigDecimal freeLength;

    @Column(name = "number_of_coils")
    private BigDecimal numberOfCoils;

    @Column(name = "active_coils")
    private BigDecimal activeCoils;

    @Column(name = "spring_rate")
    private BigDecimal springRate;

    @Column(name = "max_load")
    private BigDecimal maxLoad;

    @Column(name = "min_load")
    private BigDecimal minLoad;

    @Column(name = "working_length")
    private BigDecimal workingLength;

    @Column(name = "solid_height")
    private BigDecimal solidHeight;

    @Column(name = "end_type")
    private String endType;

    @Column(name = "surface_treatment")
    private String surfaceTreatment;

    @Column(name = "heat_treatment")
    private String heatTreatment;

    @Column(name = "tolerance")
    private String tolerance;

    @Column(name = "unit_weight_kg")
    private BigDecimal unitWeightKg;

    @Column(name = "uom", nullable = false)
    private String uom = "PCS";

    @Column(name = "drawing_number")
    private String drawingNumber;

    @Column(name = "drawing_revision")
    private String drawingRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "reorder_level", nullable = false)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "standard_cost", nullable = false)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String productCode) {
        this.productCode = productCode;
    }

    public void apply(Details d) {
        this.name = d.name();
        this.springType = d.springType();
        this.primaryMaterial = d.primaryMaterial();
        this.wireDiameter = d.wireDiameter();
        this.outerDiameter = d.outerDiameter();
        this.innerDiameter = d.innerDiameter();
        this.freeLength = d.freeLength();
        this.numberOfCoils = d.numberOfCoils();
        this.activeCoils = d.activeCoils();
        this.springRate = d.springRate();
        this.maxLoad = d.maxLoad();
        this.minLoad = d.minLoad();
        this.workingLength = d.workingLength();
        this.solidHeight = d.solidHeight();
        this.endType = d.endType();
        this.surfaceTreatment = d.surfaceTreatment();
        this.heatTreatment = d.heatTreatment();
        this.tolerance = d.tolerance();
        this.unitWeightKg = d.unitWeightKg();
        this.uom = d.uom();
        this.drawingNumber = d.drawingNumber();
        this.drawingRevision = d.drawingRevision();
        this.customer = d.customer();
        this.reorderLevel = d.reorderLevel();
        this.standardCost = d.standardCost();
    }

    /** Changes the lifecycle state; the service decides whether the transition is allowed. */
    void changeStatus(ProductStatus newStatus) {
        this.status = newStatus;
        this.active = newStatus != ProductStatus.OBSOLETE;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getName() {
        return name;
    }

    public SpringType getSpringType() {
        return springType;
    }

    public Material getPrimaryMaterial() {
        return primaryMaterial;
    }

    public BigDecimal getWireDiameter() {
        return wireDiameter;
    }

    public BigDecimal getOuterDiameter() {
        return outerDiameter;
    }

    public BigDecimal getInnerDiameter() {
        return innerDiameter;
    }

    public BigDecimal getFreeLength() {
        return freeLength;
    }

    public BigDecimal getNumberOfCoils() {
        return numberOfCoils;
    }

    public BigDecimal getActiveCoils() {
        return activeCoils;
    }

    public BigDecimal getSpringRate() {
        return springRate;
    }

    public BigDecimal getMaxLoad() {
        return maxLoad;
    }

    public BigDecimal getMinLoad() {
        return minLoad;
    }

    public BigDecimal getWorkingLength() {
        return workingLength;
    }

    public BigDecimal getSolidHeight() {
        return solidHeight;
    }

    public String getEndType() {
        return endType;
    }

    public String getSurfaceTreatment() {
        return surfaceTreatment;
    }

    public String getHeatTreatment() {
        return heatTreatment;
    }

    public String getTolerance() {
        return tolerance;
    }

    public BigDecimal getUnitWeightKg() {
        return unitWeightKg;
    }

    public String getUom() {
        return uom;
    }

    public String getDrawingNumber() {
        return drawingNumber;
    }

    public String getDrawingRevision() {
        return drawingRevision;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public BigDecimal getStandardCost() {
        return standardCost;
    }

    public boolean isActive() {
        return active;
    }
}

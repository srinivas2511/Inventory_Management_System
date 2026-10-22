package com.mfg.ims.material.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

/**
 * MDM-003 / GC-1: one alternative unit of measure of a material. {@code factorToBase} says how many units of the
 * material's own (base) UOM one unit of {@code altUom} is: 1 COIL = 250 KG has factor 250. Stock is always held and
 * valued in the base UOM; the alternative units are accepted on entry and shown on reports.
 */
@Entity
@Table(name = "uom_conversions", schema = "ims")
public class UomConversion extends BaseEntity {

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "alt_uom", nullable = false, length = 10)
    private String altUom;

    @Column(name = "factor_to_base", nullable = false, precision = 18, scale = 6)
    private BigDecimal factorToBase;

    public UomConversion() {}

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }
    public String getAltUom() { return altUom; }
    public void setAltUom(String v) { this.altUom = v; }
    public BigDecimal getFactorToBase() { return factorToBase; }
    public void setFactorToBase(BigDecimal v) { this.factorToBase = v; }
}

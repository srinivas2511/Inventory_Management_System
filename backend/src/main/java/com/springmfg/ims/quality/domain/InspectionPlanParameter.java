package com.springmfg.ims.quality.domain;

import java.math.BigDecimal;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inspection_plan_parameters", schema = "ims")
public class InspectionPlanParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private InspectionPlan plan;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "parameter_name", nullable = false, length = 60)
    private String parameterName;

    @Column(name = "specification", length = 80)
    private String specification;

    @Column(name = "nominal", precision = 14, scale = 4)
    private BigDecimal nominal;

    @Column(name = "lower_limit", precision = 14, scale = 4)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 14, scale = 4)
    private BigDecimal upperLimit;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "is_attribute", nullable = false)
    private boolean isAttribute = false;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    public InspectionPlan getPlan() { return plan; }
    public void setPlan(InspectionPlan v) { this.plan = v; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer v) { this.seq = v; }

    public String getParameterName() { return parameterName; }
    public void setParameterName(String v) { this.parameterName = v; }

    public String getSpecification() { return specification; }
    public void setSpecification(String v) { this.specification = v; }

    public BigDecimal getNominal() { return nominal; }
    public void setNominal(BigDecimal v) { this.nominal = v; }

    public BigDecimal getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(BigDecimal v) { this.lowerLimit = v; }

    public BigDecimal getUpperLimit() { return upperLimit; }
    public void setUpperLimit(BigDecimal v) { this.upperLimit = v; }

    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }

    public boolean isAttribute() { return isAttribute; }
    public void setAttribute(boolean v) { this.isAttribute = v; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean v) { this.mandatory = v; }
}

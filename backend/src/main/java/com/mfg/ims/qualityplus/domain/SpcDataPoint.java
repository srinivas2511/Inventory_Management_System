package com.mfg.ims.qualityplus.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One measurement (X-bar/R) or one attribute observation (P, C) on a control chart. */
@Entity
@Table(name = "spc_data_points", schema = "ims")
public class SpcDataPoint extends BaseEntity {

    @Column(name = "control_chart_id", nullable = false)
    private Long controlChartId;

    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "subgroup_no", nullable = false)
    private int subgroupNo;

    @Column(name = "measurement_no", nullable = false)
    private int measurementNo;

    @Column(name = "measured_value", nullable = false)
    private BigDecimal measuredValue;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "subgroup_mean")
    private BigDecimal subgroupMean;

    @Column(name = "subgroup_range")
    private BigDecimal subgroupRange;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "defect_count")
    private Integer defectCount;

    @Column(name = "point_ucl")
    private BigDecimal pointUcl;

    @Column(name = "point_lcl")
    private BigDecimal pointLcl;

    @Column(name = "out_of_control", nullable = false)
    private boolean outOfControl = false;

    @Column(name = "violation_rule", length = 60)
    private String violationRule;

    public Long getControlChartId() { return controlChartId; }
    public void setControlChartId(Long v) { this.controlChartId = v; }

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long v) { this.inspectionId = v; }

    public int getSubgroupNo() { return subgroupNo; }
    public void setSubgroupNo(int v) { this.subgroupNo = v; }

    public int getMeasurementNo() { return measurementNo; }
    public void setMeasurementNo(int v) { this.measurementNo = v; }

    public BigDecimal getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(BigDecimal v) { this.measuredValue = v; }

    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant v) { this.measuredAt = v; }

    public BigDecimal getSubgroupMean() { return subgroupMean; }
    public void setSubgroupMean(BigDecimal v) { this.subgroupMean = v; }

    public BigDecimal getSubgroupRange() { return subgroupRange; }
    public void setSubgroupRange(BigDecimal v) { this.subgroupRange = v; }

    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer v) { this.sampleSize = v; }

    public Integer getDefectCount() { return defectCount; }
    public void setDefectCount(Integer v) { this.defectCount = v; }

    public BigDecimal getPointUcl() { return pointUcl; }
    public void setPointUcl(BigDecimal v) { this.pointUcl = v; }

    public BigDecimal getPointLcl() { return pointLcl; }
    public void setPointLcl(BigDecimal v) { this.pointLcl = v; }

    public boolean isOutOfControl() { return outOfControl; }
    public void setOutOfControl(boolean v) { this.outOfControl = v; }

    public String getViolationRule() { return violationRule; }
    public void setViolationRule(String v) { this.violationRule = v; }
}

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
@Table(name = "quality_inspection_items", schema = "ims")
public class QualityInspectionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private QualityInspection inspection;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "parameter_name", nullable = false, length = 60)
    private String parameterName;

    @Column(name = "specification", length = 80)
    private String specification;

    @Column(name = "lower_limit", precision = 14, scale = 4)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 14, scale = 4)
    private BigDecimal upperLimit;

    @Column(name = "actual_value", precision = 14, scale = 4)
    private BigDecimal actualValue;

    @Column(name = "actual_text", length = 80)
    private String actualText;

    @Column(name = "unit", length = 10)
    private String unit;

    @Column(name = "result", nullable = false, length = 12)
    private String result = "NOT_MEASURED";

    @Column(name = "deviation_note", length = 300)
    private String deviationNote;

    @Column(name = "deviation_approved_by")
    private Long deviationApprovedBy;

    @Column(name = "deviation_approved_at")
    private java.time.Instant deviationApprovedAt;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    /** Compute PASS/FAIL from limits. Client-supplied result is ignored. */
    public void computeResult() {
        if (actualValue == null && actualText == null) {
            result = "NOT_MEASURED";
            return;
        }
        if (actualValue != null && (lowerLimit != null || upperLimit != null)) {
            boolean pass = (lowerLimit == null || actualValue.compareTo(lowerLimit) >= 0)
                        && (upperLimit == null || actualValue.compareTo(upperLimit) <= 0);
            result = pass ? "PASS" : "FAIL";
        } else {
            result = "PASS";
        }
    }

    public QualityInspection getInspection() { return inspection; }
    public void setInspection(QualityInspection v) { this.inspection = v; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer v) { this.seq = v; }

    public String getParameterName() { return parameterName; }
    public void setParameterName(String v) { this.parameterName = v; }

    public String getSpecification() { return specification; }
    public void setSpecification(String v) { this.specification = v; }

    public BigDecimal getLowerLimit() { return lowerLimit; }
    public void setLowerLimit(BigDecimal v) { this.lowerLimit = v; }

    public BigDecimal getUpperLimit() { return upperLimit; }
    public void setUpperLimit(BigDecimal v) { this.upperLimit = v; }

    public BigDecimal getActualValue() { return actualValue; }
    public void setActualValue(BigDecimal v) { this.actualValue = v; }

    public String getActualText() { return actualText; }
    public void setActualText(String v) { this.actualText = v; }

    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }

    public String getResult() { return result; }

    public String getDeviationNote() { return deviationNote; }
    public void setDeviationNote(String v) { this.deviationNote = v; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean v) { this.mandatory = v; }

    public Long getDeviationApprovedBy() { return deviationApprovedBy; }
    public void setDeviationApprovedBy(Long v) { this.deviationApprovedBy = v; }

    public java.time.Instant getDeviationApprovedAt() { return deviationApprovedAt; }
    public void setDeviationApprovedAt(java.time.Instant v) { this.deviationApprovedAt = v; }
}

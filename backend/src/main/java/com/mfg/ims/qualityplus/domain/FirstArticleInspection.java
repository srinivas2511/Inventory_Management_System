package com.mfg.ims.qualityplus.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A first article inspection requirement and its outcome (QMS-010). */
@Entity
@Table(name = "first_article_inspections", schema = "ims")
public class FirstArticleInspection extends BaseEntity {

    @Column(name = "fai_number", nullable = false, length = 20, unique = true)
    private String faiNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 15)
    private FaiTrigger triggerType;

    @Column(name = "eco_id")
    private Long ecoId;

    @Column(name = "trigger_reference", length = 40)
    private String triggerReference;

    @Column(name = "inspection_plan_id")
    private Long inspectionPlanId;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "fg_batch_id")
    private Long fgBatchId;

    @Column(name = "inspection_id")
    private Long inspectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private FaiStatus status = FaiStatus.REQUIRED;

    @Column(name = "result_summary", length = 1000)
    private String resultSummary;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getFaiNumber() { return faiNumber; }
    public void setFaiNumber(String v) { this.faiNumber = v; }

    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }

    public FaiTrigger getTriggerType() { return triggerType; }
    public void setTriggerType(FaiTrigger v) { this.triggerType = v; }

    public String getTriggerReference() { return triggerReference; }
    public void setTriggerReference(String v) { this.triggerReference = v; }
    public Long getEcoId() { return ecoId; }
    public void setEcoId(Long v) { this.ecoId = v; }

    public Long getInspectionPlanId() { return inspectionPlanId; }
    public void setInspectionPlanId(Long v) { this.inspectionPlanId = v; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public Long getFgBatchId() { return fgBatchId; }
    public void setFgBatchId(Long v) { this.fgBatchId = v; }

    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long v) { this.inspectionId = v; }

    public FaiStatus getStatus() { return status; }
    public void setStatus(FaiStatus v) { this.status = v; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String v) { this.resultSummary = v; }

    public Long getCompletedBy() { return completedBy; }
    public void setCompletedBy(Long v) { this.completedBy = v; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}

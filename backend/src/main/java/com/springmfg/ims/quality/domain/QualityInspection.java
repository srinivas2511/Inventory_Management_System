package com.springmfg.ims.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "quality_inspections", schema = "ims")
public class QualityInspection extends BaseEntity {

    @Column(name = "inspection_number", nullable = false, unique = true, length = 20)
    private String inspectionNumber;

    @Column(name = "inspection_type", nullable = false, length = 12)
    private String inspectionType;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "goods_receipt_item_id")
    private Long goodsReceiptItemId;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "material_batch_id")
    private Long materialBatchId;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "production_operation_id")
    private Long productionOperationId;

    @Column(name = "fg_batch_id")
    private Long fgBatchId;

    @Column(name = "inspected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal inspectedQty;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "accepted_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal acceptedQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "hold_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal holdQty = BigDecimal.ZERO;

    @Column(name = "inspector_id")
    private Long inspectorId;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @Column(name = "decision_by")
    private Long decisionBy;

    @Column(name = "decision_at")
    private Instant decisionAt;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<QualityInspectionItem> items = new ArrayList<>();

    public String getInspectionNumber() { return inspectionNumber; }
    public void setInspectionNumber(String v) { this.inspectionNumber = v; }

    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String v) { this.inspectionType = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Long getGoodsReceiptItemId() { return goodsReceiptItemId; }
    public void setGoodsReceiptItemId(Long v) { this.goodsReceiptItemId = v; }

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long v) { this.materialId = v; }

    public Long getMaterialBatchId() { return materialBatchId; }
    public void setMaterialBatchId(Long v) { this.materialBatchId = v; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public Long getProductionOperationId() { return productionOperationId; }
    public void setProductionOperationId(Long v) { this.productionOperationId = v; }

    public Long getFgBatchId() { return fgBatchId; }
    public void setFgBatchId(Long v) { this.fgBatchId = v; }

    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal v) { this.inspectedQty = v; }

    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer v) { this.sampleSize = v; }

    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal v) { this.acceptedQty = v; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal v) { this.rejectedQty = v; }

    public BigDecimal getHoldQty() { return holdQty; }
    public void setHoldQty(BigDecimal v) { this.holdQty = v; }

    public Long getInspectorId() { return inspectorId; }
    public void setInspectorId(Long v) { this.inspectorId = v; }

    public Instant getInspectedAt() { return inspectedAt; }
    public void setInspectedAt(Instant v) { this.inspectedAt = v; }

    public Long getDecisionBy() { return decisionBy; }
    public void setDecisionBy(Long v) { this.decisionBy = v; }

    public Instant getDecisionAt() { return decisionAt; }
    public void setDecisionAt(Instant v) { this.decisionAt = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public List<QualityInspectionItem> getItems() { return items; }
}

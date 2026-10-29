package com.mfg.ims.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_operations", schema = "ims")
public class ProductionOperation extends BaseEntity {

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "assigned_operator_id")
    private Long assignedOperatorId;

    @Column(name = "inspection_point", nullable = false)
    private boolean inspectionPoint = false;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "PENDING";

    @Column(name = "input_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal inputQty = BigDecimal.ZERO;

    @Column(name = "good_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal goodQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    public ProductionOperation() {}

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int v) { this.sequenceNo = v; }

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long v) { this.operationId = v; }

    public Long getMachineId() { return machineId; }
    public void setMachineId(Long v) { this.machineId = v; }

    public Long getAssignedOperatorId() { return assignedOperatorId; }
    public void setAssignedOperatorId(Long v) { this.assignedOperatorId = v; }

    public boolean isInspectionPoint() { return inspectionPoint; }
    public void setInspectionPoint(boolean v) { this.inspectionPoint = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public BigDecimal getInputQty() { return inputQty; }
    public void setInputQty(BigDecimal v) { this.inputQty = v; }

    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal v) { this.goodQty = v; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal v) { this.rejectedQty = v; }

    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal v) { this.scrapQty = v; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { this.startedAt = v; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }

    public Instant getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(Instant v) { this.scheduledStart = v; }

    public Instant getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(Instant v) { this.scheduledEnd = v; }

    public Instant getPlannedStart() { return plannedStart; }
    public void setPlannedStart(Instant v) { this.plannedStart = v; }

    public Instant getPlannedEnd() { return plannedEnd; }
    public void setPlannedEnd(Instant v) { this.plannedEnd = v; }
}

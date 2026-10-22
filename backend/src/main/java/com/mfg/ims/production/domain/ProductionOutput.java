package com.mfg.ims.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_outputs", schema = "ims")
public class ProductionOutput extends BaseEntity {

    @Column(name = "production_operation_id", nullable = false)
    private Long productionOperationId;

    @Column(name = "production_order_id", nullable = false)
    private Long productionOrderId;

    @Column(name = "entry_no", nullable = false)
    private int entryNo;

    @Column(name = "input_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal inputQty;

    @Column(name = "good_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal goodQty;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    @Column(name = "rejection_reason_id")
    private Long rejectionReasonId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "machine_id")
    private Long machineId;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "shift", length = 10)
    private String shift;

    @Column(name = "remarks", length = 300)
    private String remarks;

    @Column(name = "correction_of_id")
    private Long correctionOfId;

    public ProductionOutput() {}

    public Long getProductionOperationId() { return productionOperationId; }
    public void setProductionOperationId(Long v) { this.productionOperationId = v; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public int getEntryNo() { return entryNo; }
    public void setEntryNo(int v) { this.entryNo = v; }

    public BigDecimal getInputQty() { return inputQty; }
    public void setInputQty(BigDecimal v) { this.inputQty = v; }

    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal v) { this.goodQty = v; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal v) { this.rejectedQty = v; }

    public BigDecimal getScrapQty() { return scrapQty; }
    public void setScrapQty(BigDecimal v) { this.scrapQty = v; }

    public Long getRejectionReasonId() { return rejectionReasonId; }
    public void setRejectionReasonId(Long v) { this.rejectionReasonId = v; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long v) { this.operatorId = v; }

    public Long getMachineId() { return machineId; }
    public void setMachineId(Long v) { this.machineId = v; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant v) { this.startTime = v; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant v) { this.endTime = v; }

    public String getShift() { return shift; }
    public void setShift(String v) { this.shift = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }

    public Long getCorrectionOfId() { return correctionOfId; }
    public void setCorrectionOfId(Long v) { this.correctionOfId = v; }
}

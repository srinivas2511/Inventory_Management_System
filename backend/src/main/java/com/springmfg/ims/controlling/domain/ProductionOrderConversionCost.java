package com.springmfg.ims.controlling.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The conversion cost (labour, machine, overhead) of a production order, fixed when the order completes. The variance
 * journal and the finished-goods cost read these figures, so they cannot drift apart if a rate changes later.
 */
@Entity
@Table(name = "production_order_conversion_costs", schema = "ims")
public class ProductionOrderConversionCost {

    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    public static final String PENDING = "PENDING";
    public static final String POSTED = "POSTED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "production_order_id", nullable = false, unique = true)
    private Long productionOrderId;

    @Column(name = "cost_centre_id")
    private Long costCentreId;

    @Column(name = "actual_hours", nullable = false, precision = 18, scale = 6)
    private BigDecimal actualHours = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "machine_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal machineCost = BigDecimal.ZERO;

    @Column(name = "overhead_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal overheadCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt = Instant.now();

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public boolean needsPosting() {
        return PENDING.equals(status) || FAILED.equals(status);
    }

    public Long getId() { return id; }
    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }
    public Long getCostCentreId() { return costCentreId; }
    public void setCostCentreId(Long v) { this.costCentreId = v; }
    public BigDecimal getActualHours() { return actualHours; }
    public void setActualHours(BigDecimal v) { this.actualHours = v; }
    public BigDecimal getLabourCost() { return labourCost; }
    public void setLabourCost(BigDecimal v) { this.labourCost = v; }
    public BigDecimal getMachineCost() { return machineCost; }
    public void setMachineCost(BigDecimal v) { this.machineCost = v; }
    public BigDecimal getOverheadCost() { return overheadCost; }
    public void setOverheadCost(BigDecimal v) { this.overheadCost = v; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal v) { this.totalCost = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getJournalHeaderId() { return journalHeaderId; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public Instant getFrozenAt() { return frozenAt; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant v) { this.postedAt = v; }
}

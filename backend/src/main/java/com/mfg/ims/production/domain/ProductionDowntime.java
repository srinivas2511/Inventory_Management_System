package com.mfg.ims.production.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_downtime", schema = "ims")
public class ProductionDowntime extends BaseEntity {

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    public ProductionDowntime() {}

    public Long getMachineId() { return machineId; }
    public void setMachineId(Long v) { this.machineId = v; }

    public Long getProductionOrderId() { return productionOrderId; }
    public void setProductionOrderId(Long v) { this.productionOrderId = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }

    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { this.startedAt = v; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant v) { this.endedAt = v; }

    public Long getReportedBy() { return reportedBy; }
    public void setReportedBy(Long v) { this.reportedBy = v; }
}

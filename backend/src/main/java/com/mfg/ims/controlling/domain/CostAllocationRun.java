package com.mfg.ims.controlling.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cost_allocation_runs", schema = "ims")
public class CostAllocationRun extends BaseEntity {

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "status", nullable = false, length = 15)
    private String status = "COMPLETED";

    @Column(name = "run_at", nullable = false)
    private Instant runAt = Instant.now();

    @Column(name = "run_by", nullable = false)
    private Long runBy;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "total_allocated", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAllocated = BigDecimal.ZERO;

    public CostAllocationRun() {}

    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long v) { this.periodId = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant v) { this.runAt = v; }

    public Long getRunBy() { return runBy; }
    public void setRunBy(Long v) { this.runBy = v; }

    public Long getJournalHeaderId() { return journalHeaderId; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }

    public BigDecimal getTotalAllocated() { return totalAllocated; }
    public void setTotalAllocated(BigDecimal v) { this.totalAllocated = v; }
}

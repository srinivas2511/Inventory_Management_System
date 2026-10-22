package com.mfg.ims.finance.gl.domain;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fiscal_periods", schema = "ims")
public class FiscalPeriod extends BaseEntity {

    @Column(name = "period_name", nullable = false, length = 20)
    private String periodName;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "period_number", nullable = false)
    private Short periodNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    public String getPeriodName() { return periodName; }
    public Integer getFiscalYear() { return fiscalYear; }
    public Short getPeriodNumber() { return periodNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getStatus() { return status; }
    public Long getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }

    public void setPeriodName(String periodName) { this.periodName = periodName; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public void setPeriodNumber(Short periodNumber) { this.periodNumber = periodNumber; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setStatus(String status) { this.status = status; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}

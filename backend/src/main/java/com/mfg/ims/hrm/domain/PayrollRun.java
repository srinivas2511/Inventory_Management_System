package com.mfg.ims.hrm.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "payroll_runs", schema = "ims")
public class PayrollRun extends BaseEntity {

    @Column(name = "run_number", nullable = false, unique = true, length = 20)
    private String runNumber;

    @Column(name = "period_month", nullable = false)
    private short periodMonth;

    @Column(name = "period_year", nullable = false)
    private short periodYear;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "total_employees")
    private Integer totalEmployees;

    @Column(name = "total_gross", precision = 18, scale = 4)
    private BigDecimal totalGross;

    @Column(name = "total_net", precision = 18, scale = 4)
    private BigDecimal totalNet;

    @Column(name = "journal_header_id")
    private Long journalHeaderId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private Long paidBy;

    @Column(name = "payment_journal_header_id")
    private Long paymentJournalHeaderId;

    public String getRunNumber() { return runNumber; }
    public void setRunNumber(String v) { this.runNumber = v; }
    public short getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(short v) { this.periodMonth = v; }
    public short getPeriodYear() { return periodYear; }
    public void setPeriodYear(short v) { this.periodYear = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(Long v) { this.initiatedBy = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long v) { this.approvedBy = v; }
    public Integer getTotalEmployees() { return totalEmployees; }
    public void setTotalEmployees(Integer v) { this.totalEmployees = v; }
    public BigDecimal getTotalGross() { return totalGross; }
    public void setTotalGross(BigDecimal v) { this.totalGross = v; }
    public BigDecimal getTotalNet() { return totalNet; }
    public void setTotalNet(BigDecimal v) { this.totalNet = v; }
    public Long getJournalHeaderId() { return journalHeaderId; }
    public void setJournalHeaderId(Long v) { this.journalHeaderId = v; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public Long getPaidBy() { return paidBy; }
    public void setPaidBy(Long v) { this.paidBy = v; }
    public Long getPaymentJournalHeaderId() { return paymentJournalHeaderId; }
    public void setPaymentJournalHeaderId(Long v) { this.paymentJournalHeaderId = v; }
}

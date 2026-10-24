package com.mfg.ims.maintenance.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_predictions", schema = "ims")
public class MaintenancePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "predicted_failure_at", nullable = false)
    private Instant predictedFailureAt;

    @Column(name = "confidence_pct", nullable = false)
    private BigDecimal confidencePct;

    @Column(name = "mtbf_hours")
    private BigDecimal mtbfHours;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "algorithm_version", nullable = false, length = 10)
    private String algorithmVersion = "1.0";

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    public Long getId() { return id; }
    public Long getMachineId() { return machineId; }
    public void setMachineId(Long machineId) { this.machineId = machineId; }
    public Instant getPredictedFailureAt() { return predictedFailureAt; }
    public void setPredictedFailureAt(Instant predictedFailureAt) { this.predictedFailureAt = predictedFailureAt; }
    public BigDecimal getConfidencePct() { return confidencePct; }
    public void setConfidencePct(BigDecimal confidencePct) { this.confidencePct = confidencePct; }
    public BigDecimal getMtbfHours() { return mtbfHours; }
    public void setMtbfHours(BigDecimal mtbfHours) { this.mtbfHours = mtbfHours; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(Instant lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    public Long getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(Long acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getCompanyId() { return companyId; }
}

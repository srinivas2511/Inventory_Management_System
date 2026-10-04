package com.springmfg.ims.plm.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "ecos", schema = "ims")
public class Eco extends BaseEntity {

    @Column(name = "eco_number", nullable = false, unique = true, length = 20)
    private String ecoNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_category", nullable = false, length = 20)
    private EcoChangeCategory changeCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", nullable = false, length = 10)
    private EcoUrgency urgency = EcoUrgency.NORMAL;

    @Column(name = "effectivity_date")
    private LocalDate effectivityDate;

    @Column(name = "impact_assessment")
    private String impactAssessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EcoStatus status = EcoStatus.DRAFT;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "implemented_at")
    private Instant implementedAt;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @OneToMany(mappedBy = "eco", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<EcoAffectedItem> items = new ArrayList<>();

    public String getEcoNumber() { return ecoNumber; }
    public void setEcoNumber(String v) { this.ecoNumber = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public EcoChangeCategory getChangeCategory() { return changeCategory; }
    public void setChangeCategory(EcoChangeCategory v) { this.changeCategory = v; }
    public EcoUrgency getUrgency() { return urgency; }
    public void setUrgency(EcoUrgency v) { this.urgency = v; }
    public LocalDate getEffectivityDate() { return effectivityDate; }
    public void setEffectivityDate(LocalDate v) { this.effectivityDate = v; }
    public String getImpactAssessment() { return impactAssessment; }
    public void setImpactAssessment(String v) { this.impactAssessment = v; }
    public EcoStatus getStatus() { return status; }
    public void setStatus(EcoStatus v) { this.status = v; }
    public Long getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(Long v) { this.initiatedBy = v; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long v) { this.approvedBy = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public Instant getImplementedAt() { return implementedAt; }
    public void setImplementedAt(Instant v) { this.implementedAt = v; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String v) { this.statusReason = v; }
    public List<EcoAffectedItem> getItems() { return items; }
}

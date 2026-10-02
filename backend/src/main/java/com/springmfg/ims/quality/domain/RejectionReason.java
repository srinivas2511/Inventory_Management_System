package com.springmfg.ims.quality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rejection_reasons", schema = "ims")
public class RejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reason_code", nullable = false, unique = true, length = 20)
    private String reasonCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "applies_to", length = 15)
    private String appliesTo;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String v) { this.reasonCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getAppliesTo() { return appliesTo; }
    public void setAppliesTo(String v) { this.appliesTo = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}

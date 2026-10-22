package com.mfg.ims.crm.domain;

import java.time.Instant;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_activities", schema = "ims")
public class CrmActivity extends BaseEntity {

    @Column(name = "activity_type", nullable = false, length = 15)
    private String activityType;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "opportunity_id")
    private Long opportunityId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(length = 200)
    private String outcome;

    @Column(name = "activity_date", nullable = false)
    private Instant activityDate;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    public String getActivityType() { return activityType; }
    public Long getCustomerId() { return customerId; }
    public Long getContactId() { return contactId; }
    public Long getLeadId() { return leadId; }
    public Long getOpportunityId() { return opportunityId; }
    public String getSubject() { return subject; }
    public String getNotes() { return notes; }
    public String getOutcome() { return outcome; }
    public Instant getActivityDate() { return activityDate; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Long getOwnerId() { return ownerId; }

    public void setActivityType(String v) { this.activityType = v; }
    public void setCustomerId(Long v) { this.customerId = v; }
    public void setContactId(Long v) { this.contactId = v; }
    public void setLeadId(Long v) { this.leadId = v; }
    public void setOpportunityId(Long v) { this.opportunityId = v; }
    public void setSubject(String v) { this.subject = v; }
    public void setNotes(String v) { this.notes = v; }
    public void setOutcome(String v) { this.outcome = v; }
    public void setActivityDate(Instant v) { this.activityDate = v; }
    public void setDurationMinutes(Integer v) { this.durationMinutes = v; }
    public void setOwnerId(Long v) { this.ownerId = v; }
}

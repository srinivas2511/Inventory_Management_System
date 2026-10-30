package com.mfg.ims.finance.reminder;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Payment reminder for an open item.
 * Mapped to the {@code ims.payment_reminders} table created in V68.
 */
@Entity
@Table(name = "payment_reminders", schema = "ims")
public class PaymentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId = 1L;

    @Column(name = "open_item_id")
    private Long openItemId;

    @Column(name = "party_id")
    private Long partyId;

    /** Reminder escalation level (1=first, 2=second, 3=final). */
    @Column(name = "level")
    private Integer level;

    /** AUTO or MANUAL. */
    @Column(name = "mode", length = 8)
    private String mode;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Delivery channel: EMAIL, SMS, etc. */
    @Column(name = "channel", length = 8)
    private String channel;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ----- getters / setters -----

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Long getOpenItemId() { return openItemId; }
    public void setOpenItemId(Long v) { this.openItemId = v; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long v) { this.partyId = v; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer v) { this.level = v; }
    public String getMode() { return mode; }
    public void setMode(String v) { this.mode = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { this.sentAt = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public Long getSentBy() { return sentBy; }
    public void setSentBy(Long v) { this.sentBy = v; }
    public Instant getCreatedAt() { return createdAt; }
}

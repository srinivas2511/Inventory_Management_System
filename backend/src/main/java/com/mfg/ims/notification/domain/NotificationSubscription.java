package com.mfg.ims.notification.domain;

import com.mfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A user's override for one template and channel; without a row every channel the template allows is on. */
@Entity
@Table(name = "notification_subscriptions", schema = "ims")
public class NotificationSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_code", nullable = false, length = 40)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String v) { this.templateCode = v; }
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel v) { this.channel = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
}

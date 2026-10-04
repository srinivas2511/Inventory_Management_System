package com.springmfg.ims.notification.domain;

import java.util.EnumSet;
import java.util.Set;

import com.springmfg.ims.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_templates", schema = "ims")
public class NotificationTemplate extends BaseEntity {

    @Column(name = "template_code", nullable = false, unique = true, length = 40)
    private String templateCode;

    /** EMAIL, IN_APP or BOTH. */
    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "subject_template", length = 200)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Set<NotificationChannel> channels() {
        return switch (channel) {
            case "EMAIL" -> EnumSet.of(NotificationChannel.EMAIL);
            case "IN_APP" -> EnumSet.of(NotificationChannel.IN_APP);
            default -> EnumSet.allOf(NotificationChannel.class);
        };
    }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String v) { this.templateCode = v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String v) { this.subjectTemplate = v; }
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String v) { this.bodyTemplate = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}

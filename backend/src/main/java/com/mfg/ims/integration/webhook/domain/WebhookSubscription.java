package com.mfg.ims.integration.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.mfg.ims.common.domain.BaseEntity;

@Entity
@Table(name = "webhook_subscriptions", schema = "ims")
public class WebhookSubscription extends BaseEntity {

    @Column(name = "webhook_code", nullable = false, length = 40, updatable = false)
    private String webhookCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;

    @Column(name = "secret_cipher", nullable = false, length = 600)
    private String secretCipher;

    @Column(name = "secret_hint", nullable = false, length = 8)
    private String secretHint;

    /** Comma-separated event types, or {@code *} for every subscribable event. */
    @Column(nullable = false, length = 500)
    private String events;

    @Column(nullable = false)
    private boolean active = true;

    public String getWebhookCode() { return webhookCode; }
    public String getName() { return name; }
    public String getTargetUrl() { return targetUrl; }
    public String getSecretCipher() { return secretCipher; }
    public String getSecretHint() { return secretHint; }
    public String getEvents() { return events; }
    public boolean isActive() { return active; }

    public void setWebhookCode(String v) { this.webhookCode = v; }
    public void setName(String v) { this.name = v; }
    public void setTargetUrl(String v) { this.targetUrl = v; }
    public void setSecretCipher(String v) { this.secretCipher = v; }
    public void setSecretHint(String v) { this.secretHint = v; }
    public void setEvents(String v) { this.events = v; }
    public void setActive(boolean v) { this.active = v; }
}

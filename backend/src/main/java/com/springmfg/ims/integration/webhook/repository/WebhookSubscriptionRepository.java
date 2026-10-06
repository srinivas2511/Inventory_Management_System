package com.springmfg.ims.integration.webhook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.integration.webhook.domain.WebhookSubscription;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {

    List<WebhookSubscription> findByActiveTrue();

    boolean existsByWebhookCode(String webhookCode);
}

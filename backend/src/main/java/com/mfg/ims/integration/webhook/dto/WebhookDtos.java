package com.mfg.ims.integration.webhook.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class WebhookDtos {

    private WebhookDtos() {
    }

    public record WebhookDto(Long id, String webhookCode, String name, String targetUrl, String secretHint,
                             List<String> events, boolean active, Instant createdAt, Long version) {
    }

    /** The only response that carries a secret; it is shown once and cannot be read back. */
    public record WebhookSecretDto(WebhookDto webhook, String secret) {
    }

    public record SaveWebhookRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String targetUrl,
            @NotEmpty List<@NotBlank String> events,
            Boolean active,
            Long version) {
    }

    public record WebhookDeliveryDto(Long id, Long subscriptionId, String eventType, String eventKey, String status,
                                     short retryCount, Instant nextRetryAt, Instant attemptedAt, Instant deliveredAt,
                                     Integer responseStatus, String responseBody, String lastError,
                                     Instant createdAt) {
    }
}

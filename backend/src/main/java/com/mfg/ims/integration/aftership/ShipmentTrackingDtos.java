package com.mfg.ims.integration.aftership;

import java.time.Instant;

public final class ShipmentTrackingDtos {

    private ShipmentTrackingDtos() {}

    public record ShipmentTrackingDto(
            Long id,
            Long dispatchId,
            String courierCode,
            String trackingNumber,
            String trackingUrl,
            String status,
            String lastCheckpoint,
            Instant lastUpdated,
            Instant createdAt
    ) {}

    public record RegisterTrackingRequest(
            Long dispatchId,
            String courierCode,
            String trackingNumber
    ) {}

    public record WebhookPayload(String trackingNumber, String tag, String checkpointMessage) {}
}

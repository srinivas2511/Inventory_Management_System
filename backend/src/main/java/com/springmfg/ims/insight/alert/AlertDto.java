package com.springmfg.ims.insight.alert;

import java.time.Instant;

import com.springmfg.ims.inventory.domain.Alert;

public record AlertDto(
        Long id,
        String alertType,
        String severity,
        String entityType,
        Long entityId,
        String message,
        String status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Long acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Long resolvedBy,
        String resolutionNote
) {
    public static AlertDto from(Alert a) {
        return new AlertDto(
                a.getId(), a.getAlertType(), a.getSeverity(),
                a.getEntityType(), a.getEntityId(), a.getMessage(),
                a.getStatus(), a.getFirstSeenAt(), a.getLastSeenAt(),
                a.getAcknowledgedBy(), a.getAcknowledgedAt(),
                a.getResolvedAt(), a.getResolvedBy(), a.getResolutionNote());
    }
}

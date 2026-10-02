package com.springmfg.ims.audit.dto;

import java.time.Instant;

import com.springmfg.ims.audit.domain.AuditLog;

public record AuditLogDto(
        Long id,
        Instant occurredAt,
        Long userId,
        String username,
        String roles,
        String action,
        String entity,
        String entityId,
        String oldValue,
        String newValue,
        String reason,
        String ipAddress,
        String correlationId) {

    public static AuditLogDto from(AuditLog l) {
        return new AuditLogDto(
            l.getId(), l.getOccurredAt(), l.getUserId(), l.getUsername(),
            l.getRoles(), l.getAction(), l.getEntity(), l.getEntityId(),
            l.getOldValue(), l.getNewValue(), l.getReason(),
            l.getIpAddress(), l.getCorrelationId());
    }
}

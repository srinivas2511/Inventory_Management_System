package com.springmfg.ims.audit;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    public record AuditLogResponse(long id, Instant occurredAt, Long userId, String username, String roles,
            String action, String entity, String entityId, JsonNode oldValue, JsonNode newValue, String reason,
            String ipAddress, String correlationId) {
    }
}

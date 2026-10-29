package com.mfg.ims.integration.calllog;

import java.time.OffsetDateTime;

public record IntegrationCallDto(
        Long id,
        String connector,
        String direction,
        String requestSummary,
        Integer statusCode,
        Boolean ok,
        Integer durationMs,
        String correlationId,
        String errorMessage,
        OffsetDateTime createdAt) {

    static IntegrationCallDto from(IntegrationCall c) {
        return new IntegrationCallDto(c.getId(), c.getConnector(), c.getDirection(),
                c.getRequestSummary(), c.getStatusCode(), c.getOk(), c.getDurationMs(),
                c.getCorrelationId(), c.getErrorMessage(), c.getCreatedAt());
    }
}

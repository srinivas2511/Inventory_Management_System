package com.springmfg.ims.production.event;

import java.time.Instant;

/** Published when a production operation completes; operatorUserId is the assigned operator, else the completer. */
public record OperationCompletedEvent(Long operationId, Long productionOrderId, Long operatorUserId, Instant at) {
}

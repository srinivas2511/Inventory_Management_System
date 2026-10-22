package com.mfg.ims.production.event;

import java.time.Instant;

/** Published when a production operation starts; operatorUserId is the assigned operator, else the starter. */
public record OperationStartedEvent(Long operationId, Long productionOrderId, Long operatorUserId, Instant at) {
}

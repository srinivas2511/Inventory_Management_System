package com.mfg.ims.production.event;

import java.math.BigDecimal;

public record FinalInspectionRequiredEvent(
        Long productionOrderId,
        Long productId,
        String productionOrderNumber,
        BigDecimal plannedQty,
        BigDecimal goodQty,
        Long fgBatchId) {}

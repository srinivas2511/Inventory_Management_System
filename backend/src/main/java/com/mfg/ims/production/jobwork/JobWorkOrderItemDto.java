package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;

public record JobWorkOrderItemDto(
        Long id,
        Integer lineNo,
        Long materialId,
        Long productId,
        String description,
        BigDecimal sentQty,
        BigDecimal receivedQty,
        BigDecimal scrappedQty,
        String uom
) {}

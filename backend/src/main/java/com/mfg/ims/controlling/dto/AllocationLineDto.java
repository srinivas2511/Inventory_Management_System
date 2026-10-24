package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;

public record AllocationLineDto(
        Long id,
        Long costCentreId,
        String costCentreCode,
        Long productId,
        String productCode,
        String productName,
        String allocationBase,
        BigDecimal baseQty,
        BigDecimal ratePerUnit,
        BigDecimal allocatedAmount
) {}

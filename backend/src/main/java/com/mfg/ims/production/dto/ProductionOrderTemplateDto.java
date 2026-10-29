package com.mfg.ims.production.dto;

import java.math.BigDecimal;

public record ProductionOrderTemplateDto(
        Long id,
        String code,
        String name,
        Long productId,
        Long bomId,
        BigDecimal plannedQty,
        int leadTimeDays,
        String notes,
        boolean active
) {}

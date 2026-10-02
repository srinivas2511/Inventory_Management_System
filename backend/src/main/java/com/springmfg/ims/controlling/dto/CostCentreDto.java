package com.springmfg.ims.controlling.dto;

import java.math.BigDecimal;

public record CostCentreDto(
        Long id,
        String costCentreCode,
        String name,
        Long parentId,
        Long responsibleUserId,
        Long plantId,
        BigDecimal labourRatePerHour,
        BigDecimal machineRatePerHour,
        BigDecimal overheadPct,
        boolean active
) {}

package com.mfg.ims.controlling.dto;

public record CostAllocationRuleDto(
        Long id,
        Long costCentreId,
        String costCentreCode,
        String costCentreName,
        String allocationBase,
        boolean active
) {}

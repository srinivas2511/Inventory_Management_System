package com.springmfg.ims.finance.config.dto;

import java.math.BigDecimal;

public record TaxCodeDto(
        Long id,
        String taxCode,
        String description,
        String taxType,
        BigDecimal ratePct,
        Long glAccountId,
        boolean active
) {}

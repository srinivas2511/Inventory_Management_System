package com.springmfg.ims.sales.dto;

import java.math.BigDecimal;

public record CustomerSpecOverrideDto(
        Long id,
        Long customerId,
        Long productId,
        String parameterName,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        BigDecimal nominal,
        boolean active) {
}

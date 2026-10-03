package com.springmfg.ims.mrp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemandForecastDto(
        Long id,
        Long productId,
        String productCode,
        String productName,
        LocalDate targetDate,
        BigDecimal quantity,
        String notes) {}

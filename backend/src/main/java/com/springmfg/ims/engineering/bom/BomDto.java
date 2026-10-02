package com.springmfg.ims.engineering.bom;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BomDto(
        Long id,
        String bomNumber,
        String revision,
        Long productId,
        String productCode,
        String productName,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal baseQuantity,
        String changeReason,
        Long approvedBy,
        Instant approvedAt,
        List<BomItemDto> items,
        List<String> allowedActions) {}

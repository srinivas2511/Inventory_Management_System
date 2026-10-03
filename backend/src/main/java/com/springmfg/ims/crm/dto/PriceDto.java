package com.springmfg.ims.crm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceDto(
        Long id,
        Long customerId,
        String customerName,
        Long productId,
        String productCode,
        String productName,
        BigDecimal unitPrice,
        BigDecimal minQty,
        LocalDate validFrom,
        LocalDate validTo,
        boolean active,
        Long version) {}

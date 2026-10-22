package com.mfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AvailableBatchDto(
        Long fgBatchId,
        String batchNumber,
        String qualityStatus,
        BigDecimal availableQty,
        BigDecimal unitCost,
        LocalDate producedOn,
        String orderNumber) {}

package com.mfg.ims.controlling.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InternalOrderDto(
        Long id,
        String orderNumber,
        String description,
        String orderType,
        Long responsibleCostCentreId,
        BigDecimal budgetAmount,
        BigDecimal actualAmount,
        String status,
        Long settlementCostCentreId,
        BigDecimal settledAmount,
        Instant settledAt,
        Long settledBy,
        Long settlementJournalId,
        LocalDate startDate,
        LocalDate endDate) {}

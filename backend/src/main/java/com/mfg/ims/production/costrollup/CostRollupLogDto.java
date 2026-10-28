package com.mfg.ims.production.costrollup;

import java.math.BigDecimal;
import java.time.Instant;

public record CostRollupLogDto(
        Long id,
        Long materialId,
        BigDecimal rolledCost,
        Long bomId,
        int depth,
        Instant rolledAt
) {}

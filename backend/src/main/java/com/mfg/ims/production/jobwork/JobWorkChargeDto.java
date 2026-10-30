package com.mfg.ims.production.jobwork;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record JobWorkChargeDto(
        Long id,
        Long jwoId,
        String chargeDesc,
        BigDecimal amount,
        LocalDate chargeDate,
        Instant createdAt
) {}

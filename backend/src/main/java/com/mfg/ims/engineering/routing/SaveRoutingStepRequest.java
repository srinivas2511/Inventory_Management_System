package com.mfg.ims.engineering.routing;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveRoutingStepRequest(
        @Positive int sequenceNo,
        @NotNull Long operationId,
        Long machineId,
        BigDecimal standardTimeMin,
        BigDecimal setupTimeMin,
        boolean inspectionPoint) {}

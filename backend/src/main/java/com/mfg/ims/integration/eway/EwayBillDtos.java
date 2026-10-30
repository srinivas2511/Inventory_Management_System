package com.mfg.ims.integration.eway;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class EwayBillDtos {

    private EwayBillDtos() {}

    public record EwayBillDto(
            Long id,
            Long invoiceId,
            Long dispatchId,
            String ewayBillNumber,
            String status,
            Long transporterId,
            String vehicleNumber,
            Integer distanceKm,
            Instant generatedAt,
            Instant validUntil
    ) {}

    public record GenerateRequest(
            @NotNull Long invoiceId,
            Long transporterId,
            String vehicleNumber,
            Integer distanceKm
    ) {}
}

package com.mfg.ims.sales.challan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveChallanRequest(
        @NotNull Long customerId,
        LocalDate docDate,
        String challanKind,
        Long ocId,
        Long salesOrderId,
        String deliveryAddress,
        String vehicleNo,
        String driverName,
        String lrNo,
        String courier,
        String remarks,
        Boolean isReturnable,
        LocalDate expectedReturnDate,
        @Size(min = 1) List<ItemLine> items
) {
    public record ItemLine(
            Long materialId,
            Long productId,
            String description,
            @NotNull BigDecimal qty,
            String uom,
            BigDecimal rate,
            String batchNo,
            Long ocLineId
    ) {}
}

package com.mfg.ims.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDispatchRequest(
        @NotNull Long salesOrderId,
        @NotNull LocalDate dispatchDate,
        String transporter,
        String vehicleNo,
        String lrNumber,
        @NotNull String deliveryAddress,
        String invoiceReference,
        @NotEmpty @Valid List<DispatchLineRequest> lines,
        String notes) {

    public record DispatchLineRequest(
            @NotNull Long salesOrderItemId,
            @NotNull Long fgBatchId,
            @NotNull @Positive BigDecimal quantity,
            Long warehouseId,
            Long locationId,
            BigDecimal unitPrice) {}
}

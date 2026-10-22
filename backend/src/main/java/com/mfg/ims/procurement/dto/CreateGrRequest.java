package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateGrRequest(
        @NotNull Long purchaseOrderId,
        @NotNull LocalDate receiptDate,
        @NotNull Long warehouseId,
        String supplierChallanNo,
        String vehicleNo,
        @NotEmpty @Valid List<GrLineRequest> items
) {
    public record GrLineRequest(
            @NotNull Long poItemId,
            @NotNull @Positive BigDecimal receivedQty,
            @NotNull Long locationId,
            String heatNumber,
            String certificateNumber,
            LocalDate manufacturingDate,
            Boolean inspectionRequired,
            String uom
    ) {
        /** Without a unit: the quantity is in the material's base unit, as before alternative units existed. */
        public GrLineRequest(Long poItemId, BigDecimal receivedQty, Long locationId, String heatNumber,
                             String certificateNumber, LocalDate manufacturingDate, Boolean inspectionRequired) {
            this(poItemId, receivedQty, locationId, heatNumber, certificateNumber, manufacturingDate,
                    inspectionRequired, null);
        }
    }
}

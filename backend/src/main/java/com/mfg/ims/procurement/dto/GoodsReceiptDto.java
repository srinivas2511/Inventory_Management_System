package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GoodsReceiptDto(
        Long id,
        String grNumber,
        Long purchaseOrderId,
        String poNumber,
        LocalDate receiptDate,
        String supplierChallanNo,
        String vehicleNo,
        Long warehouseId,
        String status,
        Long receivedBy,
        List<GrItemDto> items
) {
    public record GrItemDto(
            Long id,
            Long poItemId,
            Long materialId,
            String materialCode,
            BigDecimal receivedQty,
            Long locationId,
            String heatNumber,
            String certificateNumber,
            Long materialBatchId,
            String materialBatchNumber,
            boolean inspectionRequired,
            String inspectionNumber
    ) {}
}

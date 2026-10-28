package com.mfg.ims.sales.challan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ChallanDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        String challanKind,
        Long customerId,
        Long ocId,
        Long salesOrderId,
        String deliveryAddress,
        String vehicleNo,
        String lrNo,
        String courier,
        String remarks,
        BigDecimal grandTotal,
        List<ItemDto> items
) {
    public record ItemDto(
            Long id,
            int lineNo,
            String itemKind,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            String uom,
            BigDecimal rate,
            BigDecimal lineTotal,
            String batchNo
    ) {}
}

package com.mfg.ims.procurement.purchasereturnchallan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseReturnChallanDto(
        Long id,
        String docNo,
        LocalDate docDate,
        String status,
        Long supplierId,
        Long purchaseReturnId,
        String vehicleNo,
        String lrNo,
        String remarks,
        BigDecimal grandTotal,
        List<ItemDto> items
) {
    public record ItemDto(
            Long id,
            int lineNo,
            Long materialId,
            BigDecimal qty,
            String uom,
            BigDecimal unitPrice
    ) {}
}

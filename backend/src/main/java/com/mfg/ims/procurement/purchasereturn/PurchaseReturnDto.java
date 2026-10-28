package com.mfg.ims.procurement.purchasereturn;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseReturnDto(
        Long id,
        String docNo,
        String status,
        Long supplierId,
        Long grnId,
        Long invoiceId,
        String reasonCode,
        String notes,
        Long debitNoteId,
        List<ItemDto> items) {

    public record ItemDto(
            Long id,
            int lineNo,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            Long uomId,
            BigDecimal unitPrice,
            Long batchId,
            Long locationId) {}
}

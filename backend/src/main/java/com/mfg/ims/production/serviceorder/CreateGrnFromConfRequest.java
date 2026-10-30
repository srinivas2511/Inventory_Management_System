package com.mfg.ims.production.serviceorder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request body for creating a goods-receipt inward from a service confirmation. */
public record CreateGrnFromConfRequest(
        Long warehouseId,
        Long locationId,
        LocalDate receivedDate,
        List<ItemLine> items
) {
    public record ItemLine(
            Long materialId,
            BigDecimal receivedQty,
            BigDecimal unitPrice
    ) {}
}

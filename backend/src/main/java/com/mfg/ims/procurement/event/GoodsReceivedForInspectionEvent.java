package com.mfg.ims.procurement.event;

public record GoodsReceivedForInspectionEvent(
        Long goodsReceiptItemId,
        Long materialBatchId,
        Long materialId,
        Long warehouseId,
        Long locationId,
        boolean inspectionRequired
) {}

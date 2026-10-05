package com.springmfg.ims.inventory.dto;

import java.math.BigDecimal;

/**
 * Command object for a single ledger leg. Quantity is always positive; the service interprets
 * direction from the transaction type (IN vs OUT) using the sign convention in {@link PostingCommand#isOutLeg()}.
 */
public record PostingCommand(
        String transactionType,
        String itemType,           // MATERIAL | PRODUCT
        Long materialId,
        Long productId,
        Long materialBatchId,
        Long fgBatchId,
        Long warehouseId,
        Long locationId,
        String stockStatus,
        BigDecimal quantity,       // always positive; sign is derived from transactionType
        String uom,
        BigDecimal unitCost,       // null = derive from batch/material
        String referenceType,
        Long referenceId,
        String referenceNumber,
        String remarks) {

    /** OUT-leg transaction types reduce on-hand. */
    public boolean isOutLeg() {
        return switch (transactionType) {
            case "MATERIAL_ISSUE", "MATERIAL_CONSUMPTION", "SALES_DISPATCH",
                 "SCRAP", "PRODUCTION_REJECTION", "STOCK_ADJUSTMENT_OUT", "MAINTENANCE_ISSUE" -> true;
            default -> false;
        };
    }

    /** Signed quantity: negative for OUT legs. */
    public BigDecimal signedQty() {
        return isOutLeg() ? quantity.negate() : quantity;
    }
}

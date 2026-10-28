package com.mfg.ims.inventory.dto;

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

    /**
     * OUT-leg transaction types reduce on-hand. PRODUCTION_REJECTION is an IN-leg into the SCRAP location: in-process
     * (WIP) quantity carries no ledger balance, so the scrapped units are recorded as arriving in scrap.
     */
    public boolean isOutLeg() {
        return switch (transactionType) {
            case "MATERIAL_ISSUE", "MATERIAL_CONSUMPTION", "SALES_DISPATCH",
                 "SCRAP", "STOCK_ADJUSTMENT_OUT", "MAINTENANCE_ISSUE",
                 "PURCHASE_RETURN" -> true;
            default -> false;
        };
    }

    /** Signed quantity: negative for OUT legs. */
    public BigDecimal signedQty() {
        return isOutLeg() ? quantity.negate() : quantity;
    }
}

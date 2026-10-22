package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseRequisitionDto(
        Long id,
        String prNumber,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal requestedQty,
        String uom,
        LocalDate neededBy,
        String reason,
        String status,
        Long requestedBy,
        Long convertedPoId,
        String convertedPoNumber
) {}

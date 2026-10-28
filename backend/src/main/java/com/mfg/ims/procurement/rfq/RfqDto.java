package com.mfg.ims.procurement.rfq;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RfqDto(
        Long id,
        String docNo,
        String status,
        LocalDate dueDate,
        String deliveryLocation,
        String notes,
        List<RfqItemDto> items) {

    public record RfqItemDto(
            Long id,
            int lineNo,
            Long materialId,
            Long productId,
            String description,
            BigDecimal qty,
            Long uomId,
            LocalDate requiredDate) {}
}

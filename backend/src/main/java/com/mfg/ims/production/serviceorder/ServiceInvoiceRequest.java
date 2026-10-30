package com.mfg.ims.production.serviceorder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request body for creating a sales invoice from a service confirmation. */
public record ServiceInvoiceRequest(
        LocalDate invoiceDate,
        Long customerId,
        List<LineItem> lines
) {
    public record LineItem(
            String description,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal taxRate
    ) {}
}

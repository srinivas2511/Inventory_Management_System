package com.mfg.ims.common.event;

import java.math.BigDecimal;

/** Published inside the receipt transaction that settles an AR invoice in full. */
public record InvoicePaidEvent(Long invoiceId, String invoiceNumber, Long customerId, BigDecimal totalAmount,
                               String currencyCode) {
}

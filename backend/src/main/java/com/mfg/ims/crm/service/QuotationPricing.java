package com.mfg.ims.crm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quotation line arithmetic. The net unit price is rounded to 4 places first and the line total is quantity times
 * that net price, so the sales order created on conversion (which carries only the net unit price) totals exactly
 * what the quotation showed.
 */
final class QuotationPricing {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private QuotationPricing() {}

    static BigDecimal netUnitPrice(BigDecimal unitPrice, BigDecimal discountPct) {
        BigDecimal discount = discountPct == null ? BigDecimal.ZERO : discountPct;
        return unitPrice.multiply(HUNDRED.subtract(discount)).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal lineTotal(BigDecimal quantity, BigDecimal netUnitPrice) {
        return quantity.multiply(netUnitPrice).setScale(4, RoundingMode.HALF_UP);
    }
}

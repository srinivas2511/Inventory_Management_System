package com.springmfg.ims.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuotationPricingTest {

    @Test
    @DisplayName("no discount keeps the list price")
    void noDiscount() {
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("12.5000"), null)).isEqualByComparingTo("12.5");
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("12.5000"), BigDecimal.ZERO)).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("a percentage discount reduces the unit price")
    void discount() {
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("200.0000"), new BigDecimal("10")))
                .isEqualByComparingTo("180");
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("200.0000"), new BigDecimal("100")))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the net price is rounded half-up to 4 places")
    void rounding() {
        // 10.3333 * 0.85 = 8.783305 -> 8.7833
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("10.3333"), new BigDecimal("15")))
                .isEqualByComparingTo("8.7833");
        // 0.0001 * 0.5 = 0.00005 -> 0.0001 (half-up)
        assertThat(QuotationPricing.netUnitPrice(new BigDecimal("0.0001"), new BigDecimal("50")))
                .isEqualByComparingTo("0.0001");
    }

    @Test
    @DisplayName("line total is quantity times the already-rounded net price")
    void lineTotalUsesNetPrice() {
        BigDecimal net = QuotationPricing.netUnitPrice(new BigDecimal("10.3333"), new BigDecimal("15")); // 8.7833
        BigDecimal total = QuotationPricing.lineTotal(new BigDecimal("1000"), net);
        assertThat(total).isEqualByComparingTo("8783.3");
        // a sales order line of 1000 x net price must total exactly the same
        assertThat(new BigDecimal("1000").multiply(net)).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("fractional quantities are supported (3 decimals)")
    void fractionalQuantity() {
        // 2.500 x 3.3333 = 8.33325 -> 8.3333 (half-up at 4 places)
        assertThat(QuotationPricing.lineTotal(new BigDecimal("2.500"), new BigDecimal("3.3333")))
                .isEqualByComparingTo("8.3333");
    }
}

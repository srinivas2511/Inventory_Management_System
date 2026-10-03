package com.springmfg.ims.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springmfg.ims.crm.domain.CustomerPrice;

class PriceSelectorTest {

    private static CustomerPrice price(Long customerId, String unitPrice, String minQty, String validFrom) {
        CustomerPrice p = new CustomerPrice();
        p.setCustomerId(customerId);
        p.setProductId(1L);
        p.setUnitPrice(new BigDecimal(unitPrice));
        p.setMinQty(new BigDecimal(minQty));
        p.setValidFrom(LocalDate.parse(validFrom));
        return p;
    }

    @Test
    @DisplayName("no applicable row means no price")
    void empty() {
        assertThat(PriceSelector.select(List.of())).isEmpty();
    }

    @Test
    @DisplayName("a customer-specific price beats the standard price even when it is higher")
    void customerBeatsStandard() {
        CustomerPrice standard = price(null, "9.00", "1", "2026-01-01");
        CustomerPrice customer = price(7L, "11.00", "1", "2026-01-01");
        assertThat(PriceSelector.select(List.of(standard, customer))).contains(customer);
    }

    @Test
    @DisplayName("among the same owner the highest minimum-quantity tier wins")
    void volumeTier() {
        CustomerPrice tier1 = price(7L, "10.00", "1", "2026-01-01");
        CustomerPrice tier100 = price(7L, "8.00", "100", "2026-01-01");
        assertThat(PriceSelector.select(List.of(tier1, tier100))).contains(tier100);
    }

    @Test
    @DisplayName("with equal tiers the most recent valid-from wins")
    void mostRecentWins() {
        CustomerPrice old = price(7L, "10.00", "1", "2026-01-01");
        CustomerPrice recent = price(7L, "12.00", "1", "2026-06-01");
        assertThat(PriceSelector.select(List.of(old, recent))).contains(recent);
    }

    @Test
    @DisplayName("customer ownership outranks volume tier")
    void ownershipBeforeTier() {
        CustomerPrice standardBulk = price(null, "5.00", "1000", "2026-01-01");
        CustomerPrice customerSmall = price(7L, "9.00", "1", "2026-01-01");
        assertThat(PriceSelector.select(List.of(standardBulk, customerSmall))).contains(customerSmall);
    }
}

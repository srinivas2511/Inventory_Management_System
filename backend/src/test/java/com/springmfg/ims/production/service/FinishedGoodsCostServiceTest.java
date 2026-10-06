package com.springmfg.ims.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Hand-worked fixtures for the finished-goods unit cost: (material in WIP after scrap) / good quantity, 4 dp. */
class FinishedGoodsCostServiceTest {

    private static BigDecimal cost(String material, String scrap, String good) {
        return FinishedGoodsCostService.actualUnitCost(new BigDecimal(material), new BigDecimal(scrap), new BigDecimal(good));
    }

    @Test
    @DisplayName("FGC-1: 4,400 of material over 100 good pieces is 44.0000 each")
    void exactDivision() {
        assertThat(cost("4400.0000", "0", "100")).isEqualByComparingTo("44.0000");
    }

    @Test
    @DisplayName("FGC-2: scrap relieved from WIP is netted off: (4,000 - 240) / 80 = 47.0000")
    void scrapIsNettedOff() {
        assertThat(cost("4000.0000", "240.0000", "80")).isEqualByComparingTo("47.0000");
    }

    @Test
    @DisplayName("FGC-3: 393.975 over 100 pieces is 3.93975, rounded half up to 3.9398")
    void roundsHalfUpToLedgerPrecision() {
        assertThat(cost("393.9750", "0", "100")).isEqualByComparingTo("3.9398");
    }

    @Test
    @DisplayName("FGC-4: (153.975 left in WIP) / 90 = 1.71083..., which is 1.7108")
    void roundsDown() {
        assertThat(cost("393.9750", "240.0000", "90")).isEqualByComparingTo("1.7108");
    }

    @Test
    @DisplayName("FGC-5: scrap that consumes all the material, or no good output, never gives a negative or undefined cost")
    void neverNegative() {
        assertThat(cost("240.0000", "240.0000", "10")).isEqualByComparingTo("0");
        assertThat(cost("100.0000", "240.0000", "10")).isEqualByComparingTo("0");
        assertThat(cost("100.0000", "0", "0")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("FGC-6: rounding to four decimals leaves at most 0.00005 per piece in WIP")
    void roundingResidualIsBounded() {
        BigDecimal material = new BigDecimal("393.9750");
        BigDecimal good = new BigDecimal("100");
        BigDecimal received = cost("393.9750", "0", "100").multiply(good);
        assertThat(received.subtract(material).abs()).isLessThanOrEqualTo(good.multiply(new BigDecimal("0.00005")));
    }
}

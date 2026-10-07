package com.springmfg.ims.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springmfg.ims.production.domain.ProductionOrderMaterial;

/** The consumption target, worked by hand: BOM quantity per piece (without the scrap allowance) times pieces made. */
class OrderMaterialServiceTest {

    private static ProductionOrderMaterial pom(String planned, String allowancePct) {
        ProductionOrderMaterial pom = new ProductionOrderMaterial();
        pom.setPlannedQty(new BigDecimal(planned));
        pom.setScrapPctApplied(allowancePct == null ? null : new BigDecimal(allowancePct));
        return pom;
    }

    @Test
    @DisplayName("0.8 kg per piece and no allowance: 100 pieces planned -> 80 kg; 50 pieces made -> 40.000 kg")
    void noAllowance() {
        assertThat(OrderMaterialService.consumptionTarget(pom("80.000", null), new BigDecimal("100"), new BigDecimal("50")))
                .isEqualByComparingTo("40.000");
        assertThat(OrderMaterialService.consumptionTarget(pom("80.000", "0"), new BigDecimal("100"), new BigDecimal("100")))
                .isEqualByComparingTo("80.000");
    }

    @Test
    @DisplayName("A 10% allowance is not consumed: planned 88 kg = 0.8 kg x 1.10 x 100 pieces, 50 pieces made -> 40.000 kg")
    void allowanceIsLeftover() {
        assertThat(OrderMaterialService.consumptionTarget(pom("88.000", "10"), new BigDecimal("100"), new BigDecimal("50")))
                .isEqualByComparingTo("40.000");
    }

    @Test
    @DisplayName("More pieces than planned consume proportionally more: 0.8 kg x 120 pieces = 96.000 kg")
    void overProduction() {
        assertThat(OrderMaterialService.consumptionTarget(pom("80.000", "0"), new BigDecimal("100"), new BigDecimal("120")))
                .isEqualByComparingTo("96.000");
    }

    @Test
    @DisplayName("A planned quantity that does not divide evenly rounds half up to three places: 10 kg / 3 pieces x 1 = 3.333")
    void rounding() {
        assertThat(OrderMaterialService.consumptionTarget(pom("10.000", "0"), new BigDecimal("3"), new BigDecimal("1")))
                .isEqualByComparingTo("3.333");
    }
}

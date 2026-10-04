package com.springmfg.ims.qualityplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.qualityplus.service.NelsonRules;
import com.springmfg.ims.qualityplus.service.SpcMath;

/** SPC arithmetic against hand-worked fixtures (QMS-007, QMS-008). */
class SpcMathTest {

    private static List<BigDecimal> bds(String... v) {
        return java.util.Arrays.stream(v).map(BigDecimal::new).toList();
    }

    // ---- X-bar / R ---------------------------------------------------------------------------------------------

    @Test
    void xbarR_limits_use_the_published_constants_for_n5() {
        // grand mean 10.0, R-bar 0.5; A2=0.577, D3=0, D4=2.114
        SpcMath.XbarRLimits l = SpcMath.xbarR(bds("10.0", "10.2", "9.8", "10.0", "10.0"),
                bds("0.5", "0.4", "0.6", "0.5", "0.5"), 5);
        assertThat(l.mean().cl()).isEqualByComparingTo("10.000000");
        assertThat(l.mean().ucl()).isEqualByComparingTo("10.288500");
        assertThat(l.mean().lcl()).isEqualByComparingTo("9.711500");
        assertThat(l.range().cl()).isEqualByComparingTo("0.500000");
        assertThat(l.range().ucl()).isEqualByComparingTo("1.057000");
        assertThat(l.range().lcl()).isEqualByComparingTo("0");
    }

    @Test
    void xbarR_limits_use_nonzero_D3_for_n7() {
        // R-bar 1.0: UCL_R = 1.924, LCL_R = 0.076; A2 = 0.419
        SpcMath.XbarRLimits l = SpcMath.xbarR(bds("50", "50"), bds("1", "1"), 7);
        assertThat(l.mean().ucl()).isEqualByComparingTo("50.419000");
        assertThat(l.range().ucl()).isEqualByComparingTo("1.924000");
        assertThat(l.range().lcl()).isEqualByComparingTo("0.076000");
    }

    @Test
    void xbarR_rejects_unsupported_subgroup_sizes() {
        assertThatThrownBy(() -> SpcMath.xbarR(bds("1", "2"), bds("1", "1"), 1))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> SpcMath.xbarR(bds("1", "2"), bds("1", "1"), 11))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- attribute charts ------------------------------------------------------------------------------------------

    @Test
    void pChart_limits_from_pooled_defectives() {
        // 10 defectives in 400 units: p-bar 0.025; sigma = sqrt(0.025*0.975/100) = 0.0156125
        SpcMath.Limits l = SpcMath.pChart(10, 400, new BigDecimal("100"));
        assertThat(l.cl()).isEqualByComparingTo("0.025000");
        assertThat(l.ucl()).isEqualByComparingTo("0.071837");
        assertThat(l.lcl()).isEqualByComparingTo("0");
    }

    @Test
    void pChart_limits_follow_each_points_sample_size_and_stay_within_0_and_1() {
        BigDecimal pBar = new BigDecimal("0.25");
        SpcMath.Limits small = SpcMath.pLimitsFor(pBar, new BigDecimal("10"));
        SpcMath.Limits large = SpcMath.pLimitsFor(pBar, new BigDecimal("1000"));
        assertThat(large.ucl()).isLessThan(small.ucl());
        assertThat(small.ucl()).isLessThanOrEqualTo(BigDecimal.ONE);
        assertThat(small.lcl()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // large n: 0.25 + 3*sqrt(0.25*0.75/1000) = 0.25 + 3*0.0136931 = 0.291079
        assertThat(large.ucl()).isEqualByComparingTo("0.291079");
        assertThat(large.lcl()).isEqualByComparingTo("0.208921");
    }

    @Test
    void cChart_limits_from_mean_defect_count() {
        // c-bar 5; 3*sqrt(5) = 6.708204
        SpcMath.Limits l = SpcMath.cChart(bds("4", "6", "5", "5"));
        assertThat(l.cl()).isEqualByComparingTo("5.000000");
        assertThat(l.ucl()).isEqualByComparingTo("11.708204");
        assertThat(l.lcl()).isEqualByComparingTo("0");
    }

    // ---- capability --------------------------------------------------------------------------------------------------

    @Test
    void capability_centred_process_gives_equal_cp_and_cpk() {
        // values mean 10.0, sample sd = sqrt(0.08/4) = 0.141421; spec 9.4..10.6
        SpcMath.Capability c = SpcMath.capability(bds("9.8", "10.0", "10.2", "10.0", "10.0"),
                new BigDecimal("10.6"), new BigDecimal("9.4"));
        assertThat(c.mean()).isEqualByComparingTo("10.000000");
        assertThat(c.stdDev()).isEqualByComparingTo("0.141421");
        assertThat(c.cp()).isEqualByComparingTo("1.414214");
        assertThat(c.cpu()).isEqualByComparingTo("1.414214");
        assertThat(c.cpl()).isEqualByComparingTo("1.414214");
        assertThat(c.cpk()).isEqualByComparingTo("1.414214");
        assertThat(c.count()).isEqualTo(5);
    }

    @Test
    void capability_off_centre_process_has_cpk_below_cp() {
        // USL 10.5, LSL 9.4: Cpu = 0.5/0.424264 = 1.178511, Cpl = 1.414214, Cp = 1.1/0.848528 = 1.296362
        SpcMath.Capability c = SpcMath.capability(bds("9.8", "10.0", "10.2", "10.0", "10.0"),
                new BigDecimal("10.5"), new BigDecimal("9.4"));
        assertThat(c.cp()).isEqualByComparingTo("1.296362");
        assertThat(c.cpu()).isEqualByComparingTo("1.178511");
        assertThat(c.cpl()).isEqualByComparingTo("1.414214");
        assertThat(c.cpk()).isEqualByComparingTo("1.178511");
        assertThat(c.cpk()).isLessThan(c.cp());
    }

    @Test
    void capability_with_one_spec_limit_returns_only_the_one_sided_index() {
        SpcMath.Capability c = SpcMath.capability(bds("9.8", "10.0", "10.2", "10.0", "10.0"),
                new BigDecimal("10.6"), null);
        assertThat(c.cp()).isNull();
        assertThat(c.cpl()).isNull();
        assertThat(c.cpu()).isEqualByComparingTo("1.414214");
        assertThat(c.cpk()).isEqualByComparingTo("1.414214");
    }

    @Test
    void capability_needs_a_spec_limit_a_spread_and_two_values() {
        assertThatThrownBy(() -> SpcMath.capability(bds("1", "2"), null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> SpcMath.capability(bds("5", "5", "5"), new BigDecimal("6"), new BigDecimal("4")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("identical");
        assertThatThrownBy(() -> SpcMath.capability(bds("5"), new BigDecimal("6"), new BigDecimal("4")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- Nelson rules ------------------------------------------------------------------------------------------------

    @Test
    void rule1_flags_a_point_beyond_either_control_limit_only_when_strictly_outside() {
        BigDecimal cl = new BigDecimal("10");
        BigDecimal ucl = new BigDecimal("10.3");
        BigDecimal lcl = new BigDecimal("9.7");
        assertThat(NelsonRules.evaluate(bds("10.0", "10.31"), cl, ucl, lcl, 9)).isEqualTo(NelsonRules.RULE_1);
        assertThat(NelsonRules.evaluate(bds("10.0", "9.69"), cl, ucl, lcl, 9)).isEqualTo(NelsonRules.RULE_1);
        assertThat(NelsonRules.evaluate(bds("10.0", "10.3"), cl, ucl, lcl, 9)).isNull();
        assertThat(NelsonRules.evaluate(bds("10.0", "9.7"), cl, ucl, lcl, 9)).isNull();
    }

    @Test
    void rule2_flags_a_run_of_nine_on_one_side_of_the_centre_line() {
        BigDecimal cl = new BigDecimal("10");
        BigDecimal ucl = new BigDecimal("11");
        BigDecimal lcl = new BigDecimal("9");
        List<BigDecimal> eightAbove = bds("10.1", "10.2", "10.1", "10.3", "10.1", "10.2", "10.1", "10.2");
        assertThat(NelsonRules.evaluate(eightAbove, cl, ucl, lcl, 9)).isNull();

        List<BigDecimal> nineAbove = new java.util.ArrayList<>(eightAbove);
        nineAbove.add(new BigDecimal("10.1"));
        assertThat(NelsonRules.evaluate(nineAbove, cl, ucl, lcl, 9)).isEqualTo(NelsonRules.RULE_2);

        List<BigDecimal> nineBelow = bds("9.9", "9.8", "9.9", "9.7", "9.9", "9.8", "9.9", "9.8", "9.9");
        assertThat(NelsonRules.evaluate(nineBelow, cl, ucl, lcl, 9)).isEqualTo(NelsonRules.RULE_2);
    }

    @Test
    void rule2_is_broken_by_a_point_on_the_centre_line_or_the_other_side() {
        BigDecimal cl = new BigDecimal("10");
        List<BigDecimal> onLine = bds("10.1", "10.2", "10.1", "10.3", "10.0", "10.2", "10.1", "10.2", "10.1");
        assertThat(NelsonRules.evaluate(onLine, cl, new BigDecimal("11"), new BigDecimal("9"), 9)).isNull();
    }

    @Test
    void both_rules_can_fire_together() {
        List<BigDecimal> history = bds("10.1", "10.1", "10.1", "10.1", "10.1", "10.1", "10.1", "10.1", "10.6");
        assertThat(NelsonRules.evaluate(history, new BigDecimal("10"), new BigDecimal("10.5"),
                new BigDecimal("9.5"), 9)).isEqualTo("NELSON_R1,NELSON_R2");
    }

    @Test
    void no_limits_means_no_judgement() {
        assertThat(NelsonRules.evaluate(bds("99"), null, null, null, 9)).isNull();
        assertThat(NelsonRules.evaluate(List.of(), new BigDecimal("1"), null, null, 9)).isNull();
    }
}

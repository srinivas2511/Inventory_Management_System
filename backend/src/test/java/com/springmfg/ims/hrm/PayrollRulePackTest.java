package com.springmfg.ims.hrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.hrm.service.IndiaPayrollRulePack;
import com.springmfg.ims.hrm.service.PayrollCalculator;
import com.springmfg.ims.hrm.service.PayrollRulePack;
import com.springmfg.ims.hrm.service.PayrollRulePacks;
import com.springmfg.ims.hrm.service.PayrollSettings;

/**
 * R20-5.1 regression: moving the statutory rules behind {@link PayrollRulePack} must not change a single Indian
 * figure. The expected numbers below are worked by hand from the settings in {@link #settings()}; they do not come from
 * running the calculator.
 */
class PayrollRulePackTest {

    private static final YearMonth OCT_2026 = YearMonth.of(2026, 10);

    private static PayrollSettings settings() {
        return new PayrollSettings(
                new BigDecimal("40"), BigDecimal.ZERO,
                new BigDecimal("15000"), new BigDecimal("12"), new BigDecimal("12"),
                new BigDecimal("21000"), new BigDecimal("0.75"), new BigDecimal("3.25"),
                PayrollSettings.parseSlabs("pt", "10000:0,25000:150,:200"),
                new BigDecimal("75000"),
                PayrollSettings.parseSlabs("tds", "300000:0,700000:5,1000000:10,1200000:15,1500000:20,:30"),
                new BigDecimal("700000"), new BigDecimal("4"));
    }

    /** October 2026 has 31 days and four Sundays (4, 11, 18, 25): 27 working days, all worked, so no proration. */
    private static PayrollCalculator.Inputs fullMonth(String basic) {
        return new PayrollCalculator.Inputs(OCT_2026, LocalDate.of(2020, 1, 1), null, Set.of(DayOfWeek.SUNDAY),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null,
                new BigDecimal(basic), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("High earner: PF capped at the ceiling, no ESI, top professional-tax slab, TDS through every slab")
    void highEarnerFixture() {
        // basic 100000.00, HRA 40% = 40000.00, gross 140000.00
        // PF base = min(100000, 15000) = 15000 -> employee 12% = 1800.00, employer 12% = 1800.00
        // ESI: gross 140000 > 21000 -> 0.00 / 0.00;  PT: 140000 > 25000 -> 200.00
        // TDS: monthly gross 140000 x 12 = 1,680,000 - 75,000 = 1,605,000 taxable (> rebate limit 700,000)
        //   0-3L 0 | 3L-7L 400000 x 5% = 20000 | 7L-10L 300000 x 10% = 30000 | 10L-12L 200000 x 15% = 30000
        //   12L-15L 300000 x 20% = 60000 | 15L-16.05L 105000 x 30% = 31500  => 171,500; +4% cess = 178,360
        //   178,360 / 12 = 14,863.3333 -> 14,863.33
        // deductions 1800.00 + 0.00 + 200.00 + 14863.33 = 16863.33; net 140000.00 - 16863.33 = 123136.67
        PayrollCalculator.Result r = PayrollCalculator.calculate(fullMonth("100000"), settings());

        assertThat(r.workingDays().toPlainString()).isEqualTo("27.0");
        assertThat(r.paidDays().toPlainString()).isEqualTo("27.0");
        assertThat(r.basicPay().toPlainString()).isEqualTo("100000.00");
        assertThat(r.hra().toPlainString()).isEqualTo("40000.00");
        assertThat(r.grossPay().toPlainString()).isEqualTo("140000.00");
        assertThat(r.pfEmployee().toPlainString()).isEqualTo("1800.00");
        assertThat(r.pfEmployer().toPlainString()).isEqualTo("1800.00");
        assertThat(r.esiEmployee().toPlainString()).isEqualTo("0.00");
        assertThat(r.esiEmployer().toPlainString()).isEqualTo("0.00");
        assertThat(r.professionalTax().toPlainString()).isEqualTo("200.00");
        assertThat(r.tds().toPlainString()).isEqualTo("14863.33");
        assertThat(r.totalDeductions().toPlainString()).isEqualTo("16863.33");
        assertThat(r.netPay().toPlainString()).isEqualTo("123136.67");
    }

    @Test
    @DisplayName("Low earner: ESI applies below its ceiling, middle professional-tax slab, TDS inside the rebate")
    void lowEarnerFixture() {
        // basic 10000.00, HRA 4000.00, gross 14000.00
        // PF base 10000 -> 1200.00 / 1200.00;  ESI (gross <= 21000): 14000 x 0.75% = 105.00, x 3.25% = 455.00
        // PT: 14000 <= 25000 -> 150.00;  TDS: 168,000 - 75,000 = 93,000 <= 700,000 rebate -> 0.00
        // deductions 1200.00 + 105.00 + 150.00 + 0.00 = 1455.00; net 14000.00 - 1455.00 = 12545.00
        PayrollCalculator.Result r = PayrollCalculator.calculate(fullMonth("10000"), settings());

        assertThat(r.grossPay().toPlainString()).isEqualTo("14000.00");
        assertThat(r.pfEmployee().toPlainString()).isEqualTo("1200.00");
        assertThat(r.pfEmployer().toPlainString()).isEqualTo("1200.00");
        assertThat(r.esiEmployee().toPlainString()).isEqualTo("105.00");
        assertThat(r.esiEmployer().toPlainString()).isEqualTo("455.00");
        assertThat(r.professionalTax().toPlainString()).isEqualTo("150.00");
        assertThat(r.tds().toPlainString()).isEqualTo("0.00");
        assertThat(r.totalDeductions().toPlainString()).isEqualTo("1455.00");
        assertThat(r.netPay().toPlainString()).isEqualTo("12545.00");
    }

    @Test
    @DisplayName("Passing the India pack explicitly gives exactly the default result")
    void explicitIndiaPackEqualsDefault() {
        PayrollCalculator.Inputs in = fullMonth("100000");
        assertThat(PayrollCalculator.calculate(in, settings(), IndiaPayrollRulePack.INSTANCE))
                .isEqualTo(PayrollCalculator.calculate(in, settings()));
    }

    @Test
    @DisplayName("The pack's own output matches the slip's statutory columns")
    void packOutputMatchesSlip() {
        PayrollRulePack.Statutory s = IndiaPayrollRulePack.INSTANCE.compute(new PayrollRulePack.Basis(
                new BigDecimal("10000.00"), new BigDecimal("14000.00"), new BigDecimal("10000"),
                BigDecimal.ZERO, BigDecimal.ZERO), settings());

        assertThat(s.employeeDeductions().toPlainString()).isEqualTo("1455.00");
        assertThat(s.pfEmployer().toPlainString()).isEqualTo("1200.00");
        assertThat(s.esiEmployer().toPlainString()).isEqualTo("455.00");
    }

    @Test
    @DisplayName("A zero-gross month has no statutory deductions")
    void zeroGross() {
        PayrollRulePack.Statutory s = IndiaPayrollRulePack.INSTANCE.compute(new PayrollRulePack.Basis(
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal("50000"),
                BigDecimal.ZERO, BigDecimal.ZERO), settings());

        assertThat(s.esiEmployee().signum()).isZero();
        assertThat(s.professionalTax().signum()).isZero();
        assertThat(s.tds().signum()).isZero();
    }

    @Test
    @DisplayName("The registry resolves IN, defaults when blank, and refuses an unknown pack")
    void registry() {
        assertThat(PayrollRulePacks.forCode("IN")).isSameAs(IndiaPayrollRulePack.INSTANCE);
        assertThat(PayrollRulePacks.forCode(" in ")).isSameAs(IndiaPayrollRulePack.INSTANCE);
        assertThat(PayrollRulePacks.forCode(null)).isSameAs(PayrollRulePacks.DEFAULT);
        assertThat(PayrollRulePacks.forCode("")).isSameAs(PayrollRulePacks.DEFAULT);
        assertThat(PayrollRulePacks.DEFAULT.code()).isEqualTo("IN");

        assertThatThrownBy(() -> PayrollRulePacks.forCode("US"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unknown payroll rule pack 'US'")
                .hasMessageContaining("[IN]");
    }
}

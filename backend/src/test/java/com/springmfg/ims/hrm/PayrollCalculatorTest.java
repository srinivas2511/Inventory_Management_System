package com.springmfg.ims.hrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.hrm.service.PayrollCalculator;
import com.springmfg.ims.hrm.service.PayrollCalculator.Inputs;
import com.springmfg.ims.hrm.service.PayrollCalculator.Result;
import com.springmfg.ims.hrm.service.PayrollSettings;

/**
 * Payroll arithmetic against the seeded statutory defaults. September 2026 starts on a Tuesday and has four
 * Sundays (6, 13, 20, 27), so it has 26 working days with Sunday as the weekly off.
 */
class PayrollCalculatorTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final Set<DayOfWeek> SUNDAY_OFF = EnumSet.of(DayOfWeek.SUNDAY);
    private static final Set<LocalDate> NONE = Set.of();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PayrollSettings defaults() {
        return new PayrollSettings(bd("40"), bd("0"), bd("15000"), bd("12"), bd("12"), bd("21000"), bd("0.75"),
                bd("3.25"), PayrollSettings.parseSlabs("pt", "7500:0,10000:175,:200"), bd("75000"),
                PayrollSettings.parseSlabs("tds", "300000:0,700000:5,1000000:10,1200000:15,1500000:20,:30"),
                bd("1200000"), bd("4"));
    }

    private static Inputs full(String basic) {
        return new Inputs(SEP, LocalDate.of(2020, 1, 1), null, SUNDAY_OFF, NONE, NONE, NONE, NONE, NONE, bd(basic),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static Inputs with(Inputs base, LocalDate joined, LocalDate left, Set<LocalDate> holidays,
                               Set<LocalDate> absent, Set<LocalDate> half, Set<LocalDate> paidLeave,
                               Set<LocalDate> unpaidLeave) {
        return new Inputs(base.month(), joined, left, base.weeklyOff(), holidays, absent, half, paidLeave,
                unpaidLeave, base.basicPay(), base.fixedTravelAllowance(), base.fixedOtherAllowances());
    }

    @Test
    void full_month_applies_hra_pf_esi_and_professional_tax() {
        Result r = PayrollCalculator.calculate(full("12000"), defaults());

        assertThat(r.workingDays()).isEqualByComparingTo("26.0");
        assertThat(r.paidDays()).isEqualByComparingTo("26.0");
        assertThat(r.lopDays()).isEqualByComparingTo("0.0");
        assertThat(r.basicPay()).isEqualByComparingTo("12000.00");
        assertThat(r.hra()).isEqualByComparingTo("4800.00");
        assertThat(r.grossPay()).isEqualByComparingTo("16800.00");
        assertThat(r.pfEmployee()).isEqualByComparingTo("1440.00");
        assertThat(r.pfEmployer()).isEqualByComparingTo("1440.00");
        assertThat(r.esiEmployee()).isEqualByComparingTo("126.00");
        assertThat(r.esiEmployer()).isEqualByComparingTo("546.00");
        assertThat(r.professionalTax()).isEqualByComparingTo("200.00");
        assertThat(r.tds()).isEqualByComparingTo("0.00");
        assertThat(r.totalDeductions()).isEqualByComparingTo("1766.00");
        assertThat(r.netPay()).isEqualByComparingTo("15034.00");
    }

    @Test
    void pf_is_capped_at_the_wage_ceiling_and_esi_stops_above_its_ceiling() {
        Result r = PayrollCalculator.calculate(full("30000"), defaults());

        assertThat(r.grossPay()).isEqualByComparingTo("42000.00");
        assertThat(r.pfEmployee()).isEqualByComparingTo("1800.00");
        assertThat(r.pfEmployer()).isEqualByComparingTo("1800.00");
        assertThat(r.esiEmployee()).isEqualByComparingTo("0.00");
        assertThat(r.esiEmployer()).isEqualByComparingTo("0.00");
        assertThat(r.netPay()).isEqualByComparingTo("40000.00");
    }

    @Test
    void esi_applies_at_exactly_the_ceiling_but_not_a_paisa_above() {
        Result at = PayrollCalculator.calculate(full("15000"), defaults());
        assertThat(at.grossPay()).isEqualByComparingTo("21000.00");
        assertThat(at.esiEmployee()).isEqualByComparingTo("157.50");
        assertThat(at.esiEmployer()).isEqualByComparingTo("682.50");

        Result above = PayrollCalculator.calculate(full("15001"), defaults());
        assertThat(above.grossPay()).isEqualByComparingTo("21001.40");
        assertThat(above.esiEmployee()).isEqualByComparingTo("0.00");
        assertThat(above.esiEmployer()).isEqualByComparingTo("0.00");
    }

    @Test
    void absent_days_prorate_pay_and_count_as_loss_of_pay() {
        Inputs in = with(full("26000"), LocalDate.of(2020, 1, 1), null, NONE,
                Set.of(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3)), NONE, NONE, NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.workingDays()).isEqualByComparingTo("26.0");
        assertThat(r.paidDays()).isEqualByComparingTo("24.0");
        assertThat(r.lopDays()).isEqualByComparingTo("2.0");
        assertThat(r.basicPay()).isEqualByComparingTo("24000.00");
        assertThat(r.hra()).isEqualByComparingTo("9600.00");
        assertThat(r.grossPay()).isEqualByComparingTo("33600.00");
    }

    @Test
    void half_day_costs_half_a_day_of_pay() {
        Inputs in = with(full("26000"), LocalDate.of(2020, 1, 1), null, NONE, NONE, Set.of(LocalDate.of(2026, 9, 3)),
                NONE, NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.paidDays()).isEqualByComparingTo("25.5");
        assertThat(r.lopDays()).isEqualByComparingTo("0.5");
        assertThat(r.basicPay()).isEqualByComparingTo("25500.00");
    }

    @Test
    void approved_paid_leave_overrides_an_absence_on_the_same_day() {
        LocalDate day = LocalDate.of(2026, 9, 2);
        Inputs in = with(full("26000"), LocalDate.of(2020, 1, 1), null, NONE, Set.of(day), NONE, Set.of(day), NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.lopDays()).isEqualByComparingTo("0.0");
        assertThat(r.basicPay()).isEqualByComparingTo("26000.00");
    }

    @Test
    void unpaid_leave_is_loss_of_pay_and_sundays_are_ignored() {
        Inputs in = with(full("26000"), LocalDate.of(2020, 1, 1), null, NONE, NONE, NONE, NONE,
                Set.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 7)));
        Result r = PayrollCalculator.calculate(in, defaults());

        // Sep 5 (Sat) and Sep 7 (Mon) count; Sep 6 is a Sunday.
        assertThat(r.lopDays()).isEqualByComparingTo("2.0");
        assertThat(r.basicPay()).isEqualByComparingTo("24000.00");
    }

    @Test
    void mid_month_joiner_is_paid_only_from_the_joining_date() {
        Inputs in = with(full("26000"), LocalDate.of(2026, 9, 14), null, NONE, NONE, NONE, NONE, NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        // Working days Sep 14-30: 6 + 6 + 3 = 15 of 26.
        assertThat(r.workingDays()).isEqualByComparingTo("26.0");
        assertThat(r.paidDays()).isEqualByComparingTo("15.0");
        assertThat(r.lopDays()).isEqualByComparingTo("11.0");
        assertThat(r.basicPay()).isEqualByComparingTo("15000.00");
    }

    @Test
    void leaver_is_paid_only_until_the_leaving_date() {
        Inputs in = with(full("26000"), LocalDate.of(2020, 1, 1), LocalDate.of(2026, 9, 18), NONE, NONE, NONE, NONE,
                NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        // Working days Sep 1-18: 5 + 6 + 5 = 16 of 26.
        assertThat(r.paidDays()).isEqualByComparingTo("16.0");
        assertThat(r.basicPay()).isEqualByComparingTo("16000.00");
    }

    @Test
    void a_holiday_removes_a_working_day_for_everyone_so_pay_stays_full() {
        Inputs in = with(full("25000"), LocalDate.of(2020, 1, 1), null, Set.of(LocalDate.of(2026, 9, 15)), NONE, NONE,
                NONE, NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.workingDays()).isEqualByComparingTo("25.0");
        assertThat(r.paidDays()).isEqualByComparingTo("25.0");
        assertThat(r.basicPay()).isEqualByComparingTo("25000.00");
    }

    @Test
    void amounts_round_half_up_to_two_decimals() {
        Result r = PayrollCalculator.calculate(full("12345.67"), defaults());

        assertThat(r.hra()).isEqualByComparingTo("4938.27");
        assertThat(r.grossPay()).isEqualByComparingTo("17283.94");
        assertThat(r.pfEmployee()).isEqualByComparingTo("1481.48");
        assertThat(r.esiEmployee()).isEqualByComparingTo("129.63");
        assertThat(r.esiEmployer()).isEqualByComparingTo("561.73");
        assertThat(r.netPay().scale()).isEqualTo(2);
    }

    @Test
    void fixed_allowances_prorate_with_attendance_and_enter_gross() {
        Inputs in = new Inputs(SEP, LocalDate.of(2020, 1, 1), null, SUNDAY_OFF, NONE,
                Set.of(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3)), NONE, NONE, NONE, bd("26000"), bd("2600"),
                bd("1300"));
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.travelAllowance()).isEqualByComparingTo("2400.00");
        assertThat(r.otherAllowances()).isEqualByComparingTo("1200.00");
        assertThat(r.grossPay()).isEqualByComparingTo("37200.00");
    }

    @Test
    void high_earner_pays_tds_from_the_slabs_with_cess() {
        Result r = PayrollCalculator.calculate(full("100000"), defaults());

        // Annual 1,680,000 - 75,000 = 1,605,000 taxable: 20,000 + 30,000 + 30,000 + 60,000 + 31,500 = 171,500;
        // with 4% cess 178,360; per month 14,863.33.
        assertThat(r.grossPay()).isEqualByComparingTo("140000.00");
        assertThat(r.tds()).isEqualByComparingTo("14863.33");
        assertThat(r.pfEmployee()).isEqualByComparingTo("1800.00");
        assertThat(r.totalDeductions()).isEqualByComparingTo("16863.33");
        assertThat(r.netPay()).isEqualByComparingTo("123136.67");
    }

    @Test
    void income_up_to_the_rebate_limit_pays_no_tds() {
        Result r = PayrollCalculator.calculate(full("60000"), defaults());

        // Annual 1,008,000 - 75,000 = 933,000 is within the 1,200,000 rebate.
        assertThat(r.tds()).isEqualByComparingTo("0.00");
    }

    @Test
    void employee_who_never_worked_the_month_earns_nothing() {
        Inputs in = with(full("26000"), LocalDate.of(2026, 10, 5), null, NONE, NONE, NONE, NONE, NONE);
        Result r = PayrollCalculator.calculate(in, defaults());

        assertThat(r.paidDays()).isEqualByComparingTo("0.0");
        assertThat(r.grossPay()).isEqualByComparingTo("0.00");
        assertThat(r.professionalTax()).isEqualByComparingTo("0.00");
        assertThat(r.netPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void professional_tax_follows_the_configured_slabs() {
        PayrollSettings s = defaults();
        Inputs low = full("5000");
        Result lowResult = PayrollCalculator.calculate(
                new Inputs(low.month(), low.dateOfJoining(), null, SUNDAY_OFF, NONE, NONE, NONE, NONE, NONE, bd("5000"),
                        BigDecimal.ZERO, BigDecimal.ZERO), s);
        assertThat(lowResult.grossPay()).isEqualByComparingTo("7000.00");
        assertThat(lowResult.professionalTax()).isEqualByComparingTo("0.00");

        Result mid = PayrollCalculator.calculate(full("7000"), s);
        assertThat(mid.grossPay()).isEqualByComparingTo("9800.00");
        assertThat(mid.professionalTax()).isEqualByComparingTo("175.00");
    }

    @Test
    void slabs_parse_in_order_and_reject_malformed_settings() {
        assertThat(PayrollSettings.parseSlabs("k", "7500:0,10000:175,:200")).hasSize(3);
        assertThat(PayrollSettings.parseSlabs("k", " ")).isEmpty();
        assertThatThrownBy(() -> PayrollSettings.parseSlabs("k", "10000:175,7500:0,:200"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayrollSettings.parseSlabs("k", ":175,10000:200"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayrollSettings.parseSlabs("k", "abc:1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PayrollSettings.parseSlabs("k", "7500"))
                .isInstanceOf(BusinessRuleException.class);
    }
}

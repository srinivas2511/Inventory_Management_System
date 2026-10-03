package com.springmfg.ims.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import com.springmfg.ims.hrm.service.PayrollSettings.Slab;

/**
 * Pure payroll arithmetic (DESIGN section 19.2). It touches no database and reads every rate from
 * {@link PayrollSettings}, so it is unit-testable and the statutory figures stay data.
 *
 * <p>Rounding: every rupee amount is rounded HALF_UP to 2 decimals; day counts to 1 decimal.
 */
public final class PayrollCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private PayrollCalculator() {
    }

    /**
     * Attendance facts for one employee and month. Dates in {@code absent}, {@code halfDays} and {@code holidays}
     * come from attendance rows; {@code paidLeave} and {@code unpaidLeave} from approved leave applications.
     * {@code recorded} holds every date that has an attendance row or is covered by approved leave. When
     * {@code unrecordedBefore} is set, a working day before that date with no record counts as absent (LOP); when it is
     * null, unrecorded days count as present and only explicit exceptions reduce pay.
     */
    public record Inputs(
            YearMonth month,
            LocalDate dateOfJoining,
            LocalDate dateOfLeaving,
            Set<DayOfWeek> weeklyOff,
            Set<LocalDate> holidays,
            Set<LocalDate> absent,
            Set<LocalDate> halfDays,
            Set<LocalDate> paidLeave,
            Set<LocalDate> unpaidLeave,
            Set<LocalDate> recorded,
            LocalDate unrecordedBefore,
            BigDecimal basicPay,
            BigDecimal fixedTravelAllowance,
            BigDecimal fixedOtherAllowances) {}

    public record Result(
            BigDecimal workingDays,
            BigDecimal paidDays,
            BigDecimal lopDays,
            BigDecimal basicPay,
            BigDecimal hra,
            BigDecimal da,
            BigDecimal travelAllowance,
            BigDecimal otherAllowances,
            BigDecimal grossPay,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal esiEmployee,
            BigDecimal esiEmployer,
            BigDecimal professionalTax,
            BigDecimal tds,
            BigDecimal totalDeductions,
            BigDecimal netPay) {}

    public static Result calculate(Inputs in, PayrollSettings s) {
        LocalDate first = in.month().atDay(1);
        LocalDate last = in.month().atEndOfMonth();

        int monthWorking = 0;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            if (isWorking(d, in)) {
                monthWorking++;
            }
        }

        LocalDate start = in.dateOfJoining().isAfter(first) ? in.dateOfJoining() : first;
        LocalDate end = in.dateOfLeaving() != null && in.dateOfLeaving().isBefore(last) ? in.dateOfLeaving() : last;

        int eligible = 0;
        BigDecimal lop = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!isWorking(d, in)) {
                continue;
            }
            eligible++;
            if (in.unpaidLeave().contains(d)) {
                lop = lop.add(BigDecimal.ONE);
            } else if (in.paidLeave().contains(d)) {
                continue;
            } else if (in.absent().contains(d)) {
                lop = lop.add(BigDecimal.ONE);
            } else if (in.halfDays().contains(d)) {
                lop = lop.add(HALF);
            } else if (in.unrecordedBefore() != null && d.isBefore(in.unrecordedBefore())
                    && !in.recorded().contains(d)) {
                lop = lop.add(BigDecimal.ONE);
            }
        }

        BigDecimal paidDays = BigDecimal.valueOf(eligible).subtract(lop).max(BigDecimal.ZERO);
        BigDecimal factor = monthWorking == 0 ? BigDecimal.ZERO
                : paidDays.divide(BigDecimal.valueOf(monthWorking), 10, RoundingMode.HALF_UP);

        BigDecimal basic = money(in.basicPay().multiply(factor));
        BigDecimal hra = money(basic.multiply(s.hraPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal da = money(basic.multiply(s.daPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal travel = money(in.fixedTravelAllowance().multiply(factor));
        BigDecimal other = money(in.fixedOtherAllowances().multiply(factor));
        BigDecimal gross = basic.add(hra).add(da).add(travel).add(other);

        BigDecimal pfBase = basic.min(s.pfWageCeiling());
        BigDecimal pfEmployee = money(pfBase.multiply(s.pfEmployeePct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal pfEmployer = money(pfBase.multiply(s.pfEmployerPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));

        BigDecimal esiEmployee = BigDecimal.ZERO.setScale(2);
        BigDecimal esiEmployer = BigDecimal.ZERO.setScale(2);
        if (gross.signum() > 0 && gross.compareTo(s.esiGrossCeiling()) <= 0) {
            esiEmployee = money(gross.multiply(s.esiEmployeePct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            esiEmployer = money(gross.multiply(s.esiEmployerPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        }

        BigDecimal professionalTax = gross.signum() > 0 ? professionalTax(gross, s.professionalTaxSlabs())
                : BigDecimal.ZERO.setScale(2);
        BigDecimal tds = gross.signum() > 0 ? monthlyTds(in, s) : BigDecimal.ZERO.setScale(2);

        BigDecimal deductions = pfEmployee.add(esiEmployee).add(professionalTax).add(tds);
        BigDecimal net = gross.subtract(deductions);

        return new Result(
                days(BigDecimal.valueOf(monthWorking)),
                days(paidDays),
                days(BigDecimal.valueOf(monthWorking).subtract(paidDays)),
                basic, hra, da, travel, other, gross,
                pfEmployee, pfEmployer, esiEmployee, esiEmployer, professionalTax, tds, deductions, net);
    }

    private static boolean isWorking(LocalDate d, Inputs in) {
        return !in.weeklyOff().contains(d.getDayOfWeek()) && !in.holidays().contains(d);
    }

    private static BigDecimal professionalTax(BigDecimal gross, List<Slab> slabs) {
        for (Slab slab : slabs) {
            if (slab.upTo() == null || gross.compareTo(slab.upTo()) <= 0) {
                return money(slab.value());
            }
        }
        return BigDecimal.ZERO.setScale(2);
    }

    /**
     * Simplified TDS: projects the full-month (un-prorated) gross over 12 months, deducts the standard deduction,
     * applies the slabs, rebate and cess, and spreads the annual tax evenly.
     */
    private static BigDecimal monthlyTds(Inputs in, PayrollSettings s) {
        BigDecimal basic = in.basicPay();
        BigDecimal monthlyGross = basic
                .add(basic.multiply(s.hraPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .add(basic.multiply(s.daPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .add(in.fixedTravelAllowance())
                .add(in.fixedOtherAllowances());
        BigDecimal taxable = monthlyGross.multiply(TWELVE).subtract(s.tdsStandardDeduction()).max(BigDecimal.ZERO);
        if (taxable.compareTo(s.tdsRebateLimit()) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (Slab slab : s.tdsSlabs()) {
            BigDecimal upper = slab.upTo();
            BigDecimal top = upper == null ? taxable : taxable.min(upper);
            BigDecimal portion = top.subtract(lower).max(BigDecimal.ZERO);
            tax = tax.add(portion.multiply(slab.value()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            if (upper == null || taxable.compareTo(upper) <= 0) {
                break;
            }
            lower = upper;
        }
        BigDecimal withCess = tax.multiply(BigDecimal.ONE.add(s.tdsCessPct().divide(HUNDRED, 10, RoundingMode.HALF_UP)));
        return money(withCess.divide(TWELVE, 10, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal days(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP);
    }
}

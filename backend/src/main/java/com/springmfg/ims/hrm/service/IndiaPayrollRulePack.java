package com.springmfg.ims.hrm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.springmfg.ims.hrm.service.PayrollSettings.Slab;

/**
 * Indian statutory payroll: provident fund, ESI, professional tax and a simplified TDS (DESIGN section 19.2). The
 * arithmetic and rounding are exactly those the calculator always applied; only its location changed (R20-5.1).
 * Rates, ceilings and slabs come from {@link PayrollSettings}. Every rupee amount is rounded HALF_UP to 2 decimals.
 */
public final class IndiaPayrollRulePack implements PayrollRulePack {

    public static final String CODE = "IN";
    public static final IndiaPayrollRulePack INSTANCE = new IndiaPayrollRulePack();

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    private IndiaPayrollRulePack() {
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Statutory compute(Basis basis, PayrollSettings s) {
        BigDecimal gross = basis.grossPay();

        BigDecimal pfBase = basis.basicPay().min(s.pfWageCeiling());
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
        BigDecimal tds = gross.signum() > 0 ? monthlyTds(basis, s) : BigDecimal.ZERO.setScale(2);

        return new Statutory(pfEmployee, pfEmployer, esiEmployee, esiEmployer, professionalTax, tds);
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
    private static BigDecimal monthlyTds(Basis basis, PayrollSettings s) {
        BigDecimal basic = basis.fullMonthBasic();
        BigDecimal monthlyGross = basic
                .add(basic.multiply(s.hraPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .add(basic.multiply(s.daPct()).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .add(basis.fixedTravelAllowance())
                .add(basis.fixedOtherAllowances());
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
}

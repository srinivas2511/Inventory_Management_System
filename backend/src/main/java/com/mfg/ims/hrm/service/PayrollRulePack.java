package com.mfg.ims.hrm.service;

import java.math.BigDecimal;

/**
 * A country's statutory payroll rules (R20-5.1). Attendance, proration and allowances are country-neutral and stay in
 * {@link PayrollCalculator}; a pack turns the prorated pay into statutory contributions and taxes using only the rates
 * and slabs in {@link PayrollSettings}, so a pack's numbers remain data. India ({@code IN}) is the only pack.
 */
public interface PayrollRulePack {

    /** The code stored in setting {@code hrm.payroll.rule_pack}. */
    String code();

    Statutory compute(Basis basis, PayrollSettings settings);

    /**
     * What the statutory rules are computed from: the prorated basic and gross for the month, and the un-prorated
     * full-month components used to project annual tax.
     */
    record Basis(
            BigDecimal basicPay,
            BigDecimal grossPay,
            BigDecimal fullMonthBasic,
            BigDecimal fixedTravelAllowance,
            BigDecimal fixedOtherAllowances) {}

    record Statutory(
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal esiEmployee,
            BigDecimal esiEmployer,
            BigDecimal professionalTax,
            BigDecimal tds) {

        /** Deductions taken from the employee's pay; employer contributions are not deducted. */
        public BigDecimal employeeDeductions() {
            return pfEmployee.add(esiEmployee).add(professionalTax).add(tds);
        }
    }
}

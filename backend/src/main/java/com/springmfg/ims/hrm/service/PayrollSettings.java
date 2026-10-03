package com.springmfg.ims.hrm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;

/** Statutory rates and slabs read from system_settings; nothing here is hard-coded in the calculator. */
public record PayrollSettings(
        BigDecimal hraPct,
        BigDecimal daPct,
        BigDecimal pfWageCeiling,
        BigDecimal pfEmployeePct,
        BigDecimal pfEmployerPct,
        BigDecimal esiGrossCeiling,
        BigDecimal esiEmployeePct,
        BigDecimal esiEmployerPct,
        List<Slab> professionalTaxSlabs,
        BigDecimal tdsStandardDeduction,
        List<Slab> tdsSlabs,
        BigDecimal tdsRebateLimit,
        BigDecimal tdsCessPct) {

    /** upTo is inclusive and null for the last, unbounded slab; value is an amount (PT) or a percentage (TDS). */
    public record Slab(BigDecimal upTo, BigDecimal value) {}

    public static PayrollSettings load(SystemSettingService s) {
        return new PayrollSettings(
                s.getDecimal("hrm.payroll.hra_pct", new BigDecimal("40")),
                s.getDecimal("hrm.payroll.da_pct", BigDecimal.ZERO),
                s.getDecimal("hrm.payroll.pf_wage_ceiling", new BigDecimal("15000")),
                s.getDecimal("hrm.payroll.pf_employee_pct", new BigDecimal("12")),
                s.getDecimal("hrm.payroll.pf_employer_pct", new BigDecimal("12")),
                s.getDecimal("hrm.payroll.esi_gross_ceiling", new BigDecimal("21000")),
                s.getDecimal("hrm.payroll.esi_employee_pct", new BigDecimal("0.75")),
                s.getDecimal("hrm.payroll.esi_employer_pct", new BigDecimal("3.25")),
                parseSlabs("hrm.payroll.pt_slabs", s.get("hrm.payroll.pt_slabs", "")),
                s.getDecimal("hrm.payroll.tds_standard_deduction", BigDecimal.ZERO),
                parseSlabs("hrm.payroll.tds_slabs", s.get("hrm.payroll.tds_slabs", "")),
                s.getDecimal("hrm.payroll.tds_rebate_limit", BigDecimal.ZERO),
                s.getDecimal("hrm.payroll.tds_cess_pct", BigDecimal.ZERO));
    }

    /** Parses "upTo:value,upTo:value,:value" - bounds ascending, only the last slab unbounded; blank means no slabs. */
    public static List<Slab> parseSlabs(String key, String raw) {
        List<Slab> slabs = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return slabs;
        }
        String[] parts = raw.split(",");
        BigDecimal previous = null;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            int colon = part.indexOf(':');
            if (colon < 0) {
                throw invalid(key, raw);
            }
            String bound = part.substring(0, colon).trim();
            String value = part.substring(colon + 1).trim();
            try {
                BigDecimal upTo = bound.isEmpty() ? null : new BigDecimal(bound);
                BigDecimal amount = new BigDecimal(value);
                if (upTo == null && i != parts.length - 1) {
                    throw invalid(key, raw);
                }
                if (upTo != null && previous != null && upTo.compareTo(previous) <= 0) {
                    throw invalid(key, raw);
                }
                if (amount.signum() < 0) {
                    throw invalid(key, raw);
                }
                slabs.add(new Slab(upTo, amount));
                previous = upTo;
            } catch (NumberFormatException e) {
                throw invalid(key, raw);
            }
        }
        return slabs;
    }

    private static BusinessRuleException invalid(String key, String raw) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                "Setting " + key + " is not a valid slab list (expected upTo:value pairs, ascending, last unbounded): "
                        + raw);
    }
}

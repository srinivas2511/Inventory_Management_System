package com.springmfg.ims.hrm.service;

import java.util.List;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/** Registry of the available statutory rule packs. India is the only one; adding a country adds a pack here. */
public final class PayrollRulePacks {

    public static final PayrollRulePack DEFAULT = IndiaPayrollRulePack.INSTANCE;

    private static final List<PayrollRulePack> PACKS = List.of(IndiaPayrollRulePack.INSTANCE);

    private PayrollRulePacks() {
    }

    /** The pack for a setting value; blank means the default. An unknown code is a configuration error, not a silent fallback. */
    public static PayrollRulePack forCode(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        String wanted = code.trim();
        return PACKS.stream()
                .filter(p -> p.code().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Unknown payroll rule pack '" + wanted + "' in setting hrm.payroll.rule_pack; available: "
                                + PACKS.stream().map(PayrollRulePack::code).toList()));
    }
}

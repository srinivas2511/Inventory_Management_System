package com.mfg.ims.doccore.paymentterms;

import java.math.BigDecimal;

public record PaymentTermDto(
        Long id,
        String termCode,
        String code,
        String name,
        int netDays,
        int dueDays,
        BigDecimal discountPct,
        Integer discountDays,
        boolean active) {

    public static PaymentTermDto from(PaymentTerm t) {
        return new PaymentTermDto(t.getId(), t.getTermCode(), t.getTermCode(), t.getName(),
                t.getNetDays(), t.getNetDays(), t.getDiscountPct(), t.getDiscountDays(), t.isActive());
    }
}

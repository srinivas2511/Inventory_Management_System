package com.mfg.ims.doccore.paymentterms;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SavePaymentTermRequest(
        @NotBlank @Size(max = 20) String termCode,
        @NotBlank @Size(max = 80) String name,
        @Min(0) int netDays,
        BigDecimal discountPct,
        Integer discountDays) {
}

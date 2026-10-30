package com.mfg.ims.procurement.dto;

import java.math.BigDecimal;

public record GrnMismatchDto(
        Long grnId,
        String grnNumber,
        Long invoiceId,
        String invoiceNumber,
        BigDecimal invoiceValue,
        BigDecimal grnValue,
        BigDecimal mismatchAmount
) {}

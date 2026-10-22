package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;

public record PostingResult(
        Long transactionId,
        String transactionNumber,
        BigDecimal balanceAfter,
        BigDecimal unitCost) {
}

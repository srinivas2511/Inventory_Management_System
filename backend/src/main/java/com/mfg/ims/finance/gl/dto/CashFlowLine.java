package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;

public record CashFlowLine(
        String activity,
        String journalType,
        String sourceModule,
        BigDecimal netCash) {}

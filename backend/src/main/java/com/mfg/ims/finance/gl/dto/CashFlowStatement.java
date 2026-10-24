package com.mfg.ims.finance.gl.dto;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowStatement(
        Long periodId,
        String periodName,
        List<CashFlowLine> lines,
        BigDecimal netOperating,
        BigDecimal netInvesting,
        BigDecimal netFinancing,
        BigDecimal netCashChange) {}

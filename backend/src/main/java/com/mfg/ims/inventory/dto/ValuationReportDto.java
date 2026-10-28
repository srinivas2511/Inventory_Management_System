package com.mfg.ims.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record ValuationReportDto(
        String costMethod,
        List<ValuationLineDto> lines,
        BigDecimal rmTotal,
        BigDecimal fgTotal,
        BigDecimal grandTotal
) {}

package com.mfg.ims.finance.gl.dto;

import java.time.LocalDate;

public record FiscalPeriodDto(
        Long id,
        String periodName,
        Integer fiscalYear,
        Short periodNumber,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}

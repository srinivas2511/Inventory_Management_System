package com.mfg.ims.finance.consolidation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ConsolidationDtos {

    private ConsolidationDtos() {}

    public record ConsolidationLineDto(
        String accountCode,
        String accountName,
        String accountGroup,
        BigDecimal amount
    ) {}

    public record EliminationDto(
        Long id,
        String description,
        BigDecimal amount,
        String debitAccountCode,
        String creditAccountCode
    ) {}

    public record ConsolidationReportDto(
        LocalDate from,
        LocalDate to,
        List<ConsolidationLineDto> lines,
        List<EliminationDto> eliminations,
        BigDecimal totalRevenue,
        BigDecimal totalExpense,
        BigDecimal netIncome
    ) {}

    public record AddEliminationRequest(
        Long periodId,
        String description,
        BigDecimal amount,
        String debitAccountCode,
        String creditAccountCode
    ) {}
}

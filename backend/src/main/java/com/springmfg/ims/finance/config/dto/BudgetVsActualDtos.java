package com.springmfg.ims.finance.config.dto;

import java.math.BigDecimal;
import java.util.List;

/** R9-7.7 / FIN-010: actual against budget by GL account, and the result of a budget import. */
public final class BudgetVsActualDtos {

    private BudgetVsActualDtos() {}

    /**
     * One GL account. {@code actual} follows the account's normal balance (debits less credits for an expense, credits
     * less debits for revenue). {@code variance} is actual less budget and {@code variancePct} is that as a percentage of
     * the budget (absent when nothing was budgeted). {@code favourable} is true when an expense is at or under budget or
     * revenue is at or over it.
     */
    public record BudgetVsActualRow(Long accountId, String accountCode, String accountName, String accountType,
                                    String normalBalance, BigDecimal budgeted, BigDecimal actual,
                                    BigDecimal variance, BigDecimal variancePct, boolean favourable) {}

    /** The rows of a fiscal year, or of one period of it, with the totals of the budgeted and actual columns. */
    public record BudgetVsActualDto(Integer fiscalYear, Long periodId, String periodName, List<BudgetVsActualRow> rows,
                                    BigDecimal totalBudgeted, BigDecimal totalActual) {}

    public record BudgetImportResultDto(int imported, int created, int updated) {}
}

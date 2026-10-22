package com.mfg.ims.finance.config.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.config.domain.Budget;
import com.mfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetImportResultDto;
import com.mfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetVsActualDto;
import com.mfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetVsActualRow;
import com.mfg.ims.finance.config.repository.BudgetRepository;

/**
 * R9-7.7 / FIN-010: GL-account-level actual against budget, and the budget CSV import. Actuals come from posted journal
 * lines; a reversed journal counts together with its mirror, so a reversal nets to nothing. The cost-centre dimension
 * arrives with the controlling release.
 */
@Service
public class BudgetVsActualService {

    private static final int MAX_CSV_LINES = 5000;

    private final EntityManager em;
    private final BudgetRepository budgets;
    private final AuditService audit;

    public BudgetVsActualService(EntityManager em, BudgetRepository budgets, AuditService audit) {
        this.em = em;
        this.budgets = budgets;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public BudgetVsActualDto report(int fiscalYear, Long periodId) {
        long period = periodId == null ? 0L : periodId;
        String periodName = null;
        if (period != 0L) {
            List<Object> names = em.createNativeQuery(
                    "SELECT period_name FROM ims.fiscal_periods WHERE id = :id AND fiscal_year = :fy")
                    .setParameter("id", period).setParameter("fy", fiscalYear).getResultList();
            if (names.isEmpty()) {
                throw new NotFoundException("FiscalPeriod", period);
            }
            periodName = (String) names.get(0);
        }
        String sql = """
                SELECT a.id, a.account_code, a.name, a.account_type, a.normal_balance,
                       COALESCE(b.budget, 0), COALESCE(j.dr, 0), COALESCE(j.cr, 0)
                  FROM ims.gl_accounts a
                  LEFT JOIN (SELECT gl_account_id, SUM(budgeted_amount) AS budget
                               FROM ims.budgets
                              WHERE fiscal_year = :fy AND (:period = 0 OR period_id = :period)
                              GROUP BY gl_account_id) b ON b.gl_account_id = a.id
                  LEFT JOIN (SELECT l.gl_account_id, SUM(l.debit_amount) AS dr, SUM(l.credit_amount) AS cr
                               FROM ims.journal_lines l
                               JOIN ims.journal_headers h ON h.id = l.journal_header_id
                               JOIN ims.fiscal_periods p ON p.id = h.period_id
                              WHERE h.status IN ('POSTED', 'REVERSED') AND p.fiscal_year = :fy
                                AND (:period = 0 OR h.period_id = :period)
                              GROUP BY l.gl_account_id) j ON j.gl_account_id = a.id
                 WHERE b.budget IS NOT NULL OR j.dr IS NOT NULL
                 ORDER BY a.account_code
                """;
        List<Object[]> result = em.createNativeQuery(sql).setParameter("fy", fiscalYear).setParameter("period", period)
                .getResultList();
        List<BudgetVsActualRow> rows = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (Object[] r : result) {
            BigDecimal budget = money(r[5]);
            BigDecimal debit = money(r[6]);
            BigDecimal credit = money(r[7]);
            boolean debitNormal = "DEBIT".equals(r[4]);
            BigDecimal actual = debitNormal ? debit.subtract(credit) : credit.subtract(debit);
            BigDecimal variance = actual.subtract(budget);
            BigDecimal pct = budget.signum() == 0 ? null
                    : variance.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP);
            boolean favourable = debitNormal ? actual.compareTo(budget) <= 0 : actual.compareTo(budget) >= 0;
            rows.add(new BudgetVsActualRow(((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                    (String) r[4], budget, actual, variance, pct, favourable));
            totalBudget = totalBudget.add(budget);
            totalActual = totalActual.add(actual);
        }
        return new BudgetVsActualDto(fiscalYear, period == 0L ? null : period, periodName, rows, totalBudget,
                totalActual);
    }

    /**
     * Imports a budget from CSV with the header {@code account_code,period,amount}, where {@code period} is the period
     * number (1 to 12) of the fiscal year. A budget for the same account and period is replaced. All or nothing: one bad
     * row refuses the whole file, and the message names the first few.
     */
    @Transactional
    public BudgetImportResultDto importCsv(int fiscalYear, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Choose a CSV file to import");
        }
        List<String> errors = new ArrayList<>();
        List<Object[]> parsed = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = in.readLine();
            if (header == null || !header.replace("﻿", "").trim().equalsIgnoreCase("account_code,period,amount")) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "The first line must be: account_code,period,amount");
            }
            String line;
            int n = 1;
            while ((line = in.readLine()) != null) {
                n++;
                if (n > MAX_CSV_LINES) {
                    throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                            "A budget file may have at most " + MAX_CSV_LINES + " lines");
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (cols.length != 3) {
                    errors.add("line " + n + ": expected account_code,period,amount");
                    continue;
                }
                parseRow(fiscalYear, n, cols, parsed, errors);
            }
        } catch (IOException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The file could not be read");
        }
        if (!errors.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Nothing was imported. "
                    + String.join("; ", errors.subList(0, Math.min(5, errors.size())))
                    + (errors.size() > 5 ? " (and " + (errors.size() - 5) + " more)" : ""));
        }
        int created = 0;
        int updated = 0;
        for (Object[] row : parsed) {
            Long accountId = (Long) row[0];
            Long periodId = (Long) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            Budget budget = budgets.findByGlAccountIdAndFiscalYearAndPeriodId(accountId, fiscalYear, periodId)
                    .orElse(null);
            if (budget == null) {
                budget = new Budget();
                budget.setGlAccountId(accountId);
                budget.setFiscalYear(fiscalYear);
                budget.setPeriodId(periodId);
                budget.setBudgetName("FY" + fiscalYear + " budget");
                created++;
            } else {
                updated++;
            }
            budget.setBudgetedAmount(amount);
            budgets.save(budget);
        }
        audit.record("BUDGET_IMPORTED", "BUDGET", String.valueOf(fiscalYear), null,
                created + " created, " + updated + " updated", file.getOriginalFilename());
        return new BudgetImportResultDto(parsed.size(), created, updated);
    }

    @SuppressWarnings("unchecked")
    private void parseRow(int fiscalYear, int n, String[] cols, List<Object[]> parsed, List<String> errors) {
        String code = cols[0].trim();
        List<Object> accounts = em.createNativeQuery("SELECT id FROM ims.gl_accounts WHERE account_code = :c")
                .setParameter("c", code).getResultList();
        if (accounts.isEmpty()) {
            errors.add("line " + n + ": unknown account " + code);
            return;
        }
        int number;
        try {
            number = Integer.parseInt(cols[1].trim());
        } catch (NumberFormatException e) {
            errors.add("line " + n + ": the period must be a number from 1 to 12");
            return;
        }
        List<Object> periods = em.createNativeQuery(
                "SELECT id FROM ims.fiscal_periods WHERE fiscal_year = :fy AND period_number = :n AND company_id = 1")
                .setParameter("fy", fiscalYear).setParameter("n", number).getResultList();
        if (periods.isEmpty()) {
            errors.add("line " + n + ": fiscal year " + fiscalYear + " has no period " + number);
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(cols[2].trim()).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            errors.add("line " + n + ": the amount is not a number");
            return;
        }
        if (amount.signum() < 0) {
            errors.add("line " + n + ": the amount cannot be negative");
            return;
        }
        parsed.add(new Object[] {((Number) accounts.get(0)).longValue(), ((Number) periods.get(0)).longValue(), amount});
    }

    private static BigDecimal money(Object o) {
        return o == null ? BigDecimal.ZERO : ((BigDecimal) o).setScale(4, RoundingMode.HALF_UP);
    }
}

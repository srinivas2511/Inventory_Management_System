package com.mfg.ims.finance.gl.service;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.gl.domain.GlAccount;
import com.mfg.ims.finance.gl.domain.FiscalPeriod;
import com.mfg.ims.finance.gl.dto.*;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
import com.mfg.ims.finance.gl.repository.JournalLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FinancialStatementsService {

    private final JournalLineRepository journalLineRepo;
    private final FiscalPeriodRepository periodRepo;
    private final GlAccountRepository glAccountRepo;

    public FinancialStatementsService(JournalLineRepository journalLineRepo,
                                      FiscalPeriodRepository periodRepo,
                                      GlAccountRepository glAccountRepo) {
        this.journalLineRepo = journalLineRepo;
        this.periodRepo = periodRepo;
        this.glAccountRepo = glAccountRepo;
    }

    public ProfitLossStatement getProfitLoss(Long periodId) {
        FiscalPeriod period = requirePeriod(periodId);
        Map<Long, GlAccount> accounts = accountMap();

        List<FinancialStatementRow> revenue = new ArrayList<>();
        List<FinancialStatementRow> cogs = new ArrayList<>();
        List<FinancialStatementRow> expenses = new ArrayList<>();

        for (Object[] row : journalLineRepo.plByFiscalYearUpToPeriod(periodId)) {
            Long id = toLong(row[0]);
            BigDecimal dr = toBd(row[1]);
            BigDecimal cr = toBd(row[2]);
            GlAccount acc = accounts.get(id);
            if (acc == null) continue;

            switch (acc.getAccountType()) {
                case "REVENUE"  -> revenue.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                        acc.getName(), acc.getAccountType(), cr.subtract(dr)));
                case "COGS"     -> cogs.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                        acc.getName(), acc.getAccountType(), dr.subtract(cr)));
                case "EXPENSE"  -> expenses.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                        acc.getName(), acc.getAccountType(), dr.subtract(cr)));
                default -> { /* ignore balance-sheet accounts */ }
            }
        }

        BigDecimal totalRevenue  = sumBalance(revenue);
        BigDecimal totalCogs     = sumBalance(cogs);
        BigDecimal grossProfit   = totalRevenue.subtract(totalCogs);
        BigDecimal totalExpenses = sumBalance(expenses);
        BigDecimal netIncome     = grossProfit.subtract(totalExpenses);

        return new ProfitLossStatement(period.getId(), period.getPeriodName(), period.getFiscalYear(),
                revenue, cogs, expenses, totalRevenue, totalCogs, grossProfit, totalExpenses, netIncome);
    }

    public BalanceSheetStatement getBalanceSheet(Long periodId) {
        FiscalPeriod period = requirePeriod(periodId);
        Map<Long, GlAccount> accounts = accountMap();

        List<FinancialStatementRow> assets      = new ArrayList<>();
        List<FinancialStatementRow> liabilities = new ArrayList<>();
        List<FinancialStatementRow> equity      = new ArrayList<>();
        BigDecimal retainedEarnings = BigDecimal.ZERO;

        for (Object[] row : journalLineRepo.allAccountsCumulativeUpToPeriod(periodId)) {
            Long id = toLong(row[0]);
            BigDecimal dr = toBd(row[1]);
            BigDecimal cr = toBd(row[2]);
            GlAccount acc = accounts.get(id);
            if (acc == null) continue;

            switch (acc.getAccountType()) {
                case "ASSET" -> assets.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                    acc.getName(), acc.getAccountType(), dr.subtract(cr)));
                case "LIABILITY" -> liabilities.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                    acc.getName(), acc.getAccountType(), cr.subtract(dr)));
                case "EQUITY" -> equity.add(new FinancialStatementRow(id, acc.getAccountCode(),
                                    acc.getName(), acc.getAccountType(), cr.subtract(dr)));
                // P&L accounts roll into retained earnings on the balance sheet
                case "REVENUE" -> retainedEarnings = retainedEarnings.add(cr.subtract(dr));
                case "COGS", "EXPENSE" -> retainedEarnings = retainedEarnings.subtract(dr.subtract(cr));
                default -> { /* ignore unknown */ }
            }
        }

        BigDecimal totalAssets               = sumBalance(assets);
        BigDecimal totalLiabilities          = sumBalance(liabilities);
        BigDecimal totalEquity               = sumBalance(equity).add(retainedEarnings);
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);

        return new BalanceSheetStatement(period.getId(), period.getPeriodName(),
                assets, liabilities, equity,
                totalAssets, totalLiabilities, retainedEarnings, totalEquity, totalLiabilitiesAndEquity);
    }

    public CashFlowStatement getCashFlow(Long periodId) {
        FiscalPeriod period = requirePeriod(periodId);

        List<CashFlowLine> lines = new ArrayList<>();
        for (Object[] row : journalLineRepo.cashFlowByPeriod(periodId)) {
            String journalType  = (String) row[0];
            String sourceModule = (String) row[1];
            BigDecimal dr = toBd(row[2]);
            BigDecimal cr = toBd(row[3]);
            String activity = classifyActivity(journalType);
            // For a bank (ASSET) account: debit = cash in, credit = cash out → net = debit − credit
            lines.add(new CashFlowLine(activity,
                    journalType  != null ? journalType  : "",
                    sourceModule != null ? sourceModule : "",
                    dr.subtract(cr)));
        }

        BigDecimal netOperating  = sumLines(lines, "OPERATING");
        BigDecimal netInvesting  = sumLines(lines, "INVESTING");
        BigDecimal netFinancing  = sumLines(lines, "FINANCING");
        BigDecimal netCashChange = netOperating.add(netInvesting).add(netFinancing);

        return new CashFlowStatement(period.getId(), period.getPeriodName(),
                lines, netOperating, netInvesting, netFinancing, netCashChange);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String classifyActivity(String journalType) {
        if (journalType == null) return "OPERATING";
        return switch (journalType) {
            case "ASSET_PURCHASE", "ASSET_DISPOSAL" -> "INVESTING";
            case "LOAN_RECEIPT", "LOAN_REPAYMENT", "EQUITY_INJECTION" -> "FINANCING";
            default -> "OPERATING";
        };
    }

    private FiscalPeriod requirePeriod(Long periodId) {
        return periodRepo.findById(periodId)
                .orElseThrow(() -> new NotFoundException("FiscalPeriod", periodId));
    }

    private Map<Long, GlAccount> accountMap() {
        return glAccountRepo.findAll().stream()
                .collect(Collectors.toMap(GlAccount::getId, Function.identity()));
    }

    private static BigDecimal sumBalance(List<FinancialStatementRow> rows) {
        return rows.stream().map(FinancialStatementRow::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumLines(List<CashFlowLine> lines, String activity) {
        return lines.stream().filter(l -> activity.equals(l.activity()))
                .map(CashFlowLine::netCash)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Long toLong(Object v) {
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private static BigDecimal toBd(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}

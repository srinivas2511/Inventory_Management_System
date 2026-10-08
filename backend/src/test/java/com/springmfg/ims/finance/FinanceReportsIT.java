package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.finance.config.dto.BudgetRequest;
import com.springmfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetImportResultDto;
import com.springmfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetVsActualDto;
import com.springmfg.ims.finance.config.dto.BudgetVsActualDtos.BudgetVsActualRow;
import com.springmfg.ims.finance.config.service.BudgetService;
import com.springmfg.ims.finance.config.service.BudgetVsActualService;
import com.springmfg.ims.finance.gl.dto.FiscalPeriodDto;
import com.springmfg.ims.finance.gl.dto.JournalCreateRequest;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.dto.TrialBalanceRow;
import com.springmfg.ims.finance.gl.service.FiscalPeriodService;
import com.springmfg.ims.finance.gl.service.GlAccountResolver;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * R9-7.7 budget against actual, the period close and reopen reason (DESIGN §15.6), and the trial balance after a
 * reversal. Each test works in a fiscal year of its own, so the numbers are exact however many other tests post
 * journals. Not {@code @Transactional}: each service call commits, as it does for a user.
 */
class FinanceReportsIT extends AbstractIntegrationTest {

    @Autowired FiscalPeriodService periods;
    @Autowired JournalService journals;
    @Autowired BudgetService budgets;
    @Autowired BudgetVsActualService vsActual;
    @Autowired GlAccountResolver glAccounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    // ── DESIGN §15.6: a reason for closing and reopening a period ─────────────

    @Test
    @DisplayName("PER-1: finalizing a close and reopening a period each need a reason, and the reason is in the audit trail")
    void closeAndReopenNeedAReasonThatIsAudited() {
        long period = period(2092, 1);

        assertThat(periods.initiatePeriodClose(period).status()).isEqualTo("CLOSING");
        assertThatThrownBy(() -> periods.finalizePeriodClose(period, "  "))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> periods.finalizePeriodClose(period, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(status(period)).as("a refused close changes nothing").isEqualTo("CLOSING");

        FiscalPeriodDto closed = periods.finalizePeriodClose(period, "  April review signed off  ");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(auditReason("PERIOD_CLOSED", period)).isEqualTo("April review signed off");

        assertThatThrownBy(() -> periods.reopenPeriod(period, ""))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(status(period)).isEqualTo("CLOSED");
        assertThat(periods.reopenPeriod(period, "Late supplier invoice").status()).isEqualTo("OPEN");
        assertThat(auditReason("PERIOD_REOPENED", period)).isEqualTo("Late supplier invoice");
    }

    // ── a reversal nets to nothing in the trial balance ─────────────────────

    @Test
    @DisplayName("TB-REV: a reversed journal still counts in its own period, and its mirror counts in the period it was posted in")
    void trialBalanceKeepsAReversedJournal() {
        long period = period(2091, 1);
        JournalHeaderDto posted = postedJournal(LocalDate.of(2091, 4, 10), "5201", "1201", "3000");

        TrialBalanceRow before = trialBalanceRow(period, "5201");
        assertThat(before.totalDebit()).isEqualByComparingTo("3000");
        assertThat(before.totalCredit()).isEqualByComparingTo("0");

        JournalHeaderDto mirror = journals.reverse(posted.id(), "Wrong account");
        long mirrorPeriod = jdbc.queryForObject("SELECT period_id FROM ims.journal_headers WHERE id = ?", Long.class,
                mirror.id());
        assertThat(mirrorPeriod).as("the mirror is posted today, in the open period of today, not in 2091").isNotEqualTo(period);

        TrialBalanceRow original = trialBalanceRow(period, "5201");
        assertThat(original.totalDebit()).as("the REVERSED original must not drop out of its own period")
                .isEqualByComparingTo("3000");
        assertThat(trialBalanceRow(mirrorPeriod, "5201").totalCredit()).as("the mirror credits the same account")
                .isGreaterThanOrEqualTo(new BigDecimal("3000"));
    }

    // ── R9-7.7: budget against actual ─────────────────────────────────────────

    @Test
    @DisplayName("BVA-1: actual follows the normal balance; variance is actual less budget, in amount and percent")
    void budgetAgainstActualByAccountAndPeriod() {
        long p1 = period(2090, 1);
        long p2 = period(2090, 2);
        budget("5201", 2090, p1, "10000");
        budget("5201", 2090, p2, "4000");
        budget("4001", 2090, p1, "10000");
        JournalHeaderDto expense = postedJournal(LocalDate.of(2090, 4, 10), "5201", "1201", "12500");
        postedJournal(LocalDate.of(2090, 4, 12), "1201", "4001", "8000");

        BudgetVsActualDto year = vsActual.report(2090, null);

        BudgetVsActualRow salary = row(year, "5201");
        assertThat(salary.budgeted()).isEqualByComparingTo("14000");
        assertThat(salary.actual()).isEqualByComparingTo("12500");
        assertThat(salary.variance()).isEqualByComparingTo("-1500");
        assertThat(salary.variancePct()).isEqualByComparingTo("-10.71");
        assertThat(salary.favourable()).as("an expense under budget").isTrue();
        BudgetVsActualRow sales = row(year, "4001");
        assertThat(sales.actual()).as("revenue is credits less debits").isEqualByComparingTo("8000");
        assertThat(sales.variance()).isEqualByComparingTo("-2000");
        assertThat(sales.variancePct()).isEqualByComparingTo("-20.00");
        assertThat(sales.favourable()).as("revenue under budget").isFalse();
        BudgetVsActualRow bank = row(year, "1201");
        assertThat(bank.budgeted()).isEqualByComparingTo("0");
        assertThat(bank.actual()).isEqualByComparingTo("-4500");
        assertThat(bank.variancePct()).as("nothing was budgeted").isNull();
        assertThat(year.totalBudgeted()).isEqualByComparingTo("24000");
        assertThat(year.totalActual()).isEqualByComparingTo("16000");

        BudgetVsActualDto first = vsActual.report(2090, p1);
        assertThat(first.periodName()).isEqualTo("FRT-2090-1");
        BudgetVsActualRow overspent = row(first, "5201");
        assertThat(overspent.budgeted()).isEqualByComparingTo("10000");
        assertThat(overspent.variance()).isEqualByComparingTo("2500");
        assertThat(overspent.variancePct()).isEqualByComparingTo("25.00");
        assertThat(overspent.favourable()).as("an expense over budget").isFalse();

        journals.reverse(expense.id(), "Entered twice");
        BudgetVsActualRow reversed = row(vsActual.report(2090, p1), "5201");
        assertThat(reversed.actual()).as("the reversed journal still counts in its own period; the mirror counts where it was posted")
                .isEqualByComparingTo("12500");
    }

    @Test
    @DisplayName("BVA-2: a budget CSV replaces matching rows, adds new ones, and a bad row refuses the whole file")
    void budgetCsvImportIsAllOrNothing() {
        long p1 = period(2093, 1);
        long p2 = period(2093, 2);
        budget("5201", 2093, p1, "10000");
        budget("5201", 2093, p2, "4000");

        BudgetImportResultDto ok = vsActual.importCsv(2093, csv("account_code,period,amount\n5201,1,11000\n4001,2,9000\n"));

        assertThat(ok.imported()).isEqualTo(2);
        assertThat(ok.created()).as("4001 in period 2 is new").isEqualTo(1);
        assertThat(ok.updated()).as("5201 in period 1 is replaced").isEqualTo(1);
        assertThat(budgetAmount("5201", 2093, p1)).isEqualByComparingTo("11000");
        assertThat(budgetAmount("4001", 2093, p2)).isEqualByComparingTo("9000");

        assertThatThrownBy(() -> vsActual.importCsv(2093, csv("account_code,period,amount\n5201,2,1\n9999,1,5\n")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Nothing was imported").hasMessageContaining("line 3: unknown account 9999");
        assertThat(budgetAmount("5201", 2093, p2)).as("the good row of a refused file is not applied")
                .isEqualByComparingTo("4000");
        assertThatThrownBy(() -> vsActual.importCsv(2093, csv("code,period,amount\n5201,1,1\n")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("account_code,period,amount");
        assertThatThrownBy(() -> vsActual.importCsv(2093, csv("account_code,period,amount\n5201,13,1\n")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has no period 13");
        assertThatThrownBy(() -> vsActual.importCsv(2093, csv("account_code,period,amount\n5201,1,-5\n")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be negative");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** An open period of its own in a distant fiscal year, reset to OPEN every time. */
    private long period(int year, int number) {
        LocalDate start = LocalDate.of(year, 3 + number, 1);
        jdbc.update("""
                INSERT INTO ims.fiscal_periods (period_name, fiscal_year, period_number, start_date, end_date, status, company_id)
                VALUES (?, ?, ?, ?, ?, 'OPEN', 1) ON CONFLICT DO NOTHING
                """, "FRT-" + year + "-" + number, year, (short) number, start, start.plusMonths(1).minusDays(1));
        jdbc.update("UPDATE ims.fiscal_periods SET status = 'OPEN', closed_by = NULL, closed_at = NULL "
                + "WHERE fiscal_year = ? AND period_number = ?", year, (short) number);
        return jdbc.queryForObject("SELECT id FROM ims.fiscal_periods WHERE fiscal_year = ? AND period_number = ?",
                Long.class, year, (short) number);
    }

    private String status(long periodId) {
        return jdbc.queryForObject("SELECT status FROM ims.fiscal_periods WHERE id = ?", String.class, periodId);
    }

    private String auditReason(String action, long periodId) {
        return jdbc.queryForObject("SELECT reason FROM ims.audit_logs WHERE action = ? AND entity_id = ? LIMIT 1",
                String.class, action, String.valueOf(periodId));
    }

    private void budget(String accountCode, int year, long periodId, String amount) {
        budgets.saveBudget(new BudgetRequest(glAccounts.idOf(accountCode), year, periodId, new BigDecimal(amount), null));
    }

    private BigDecimal budgetAmount(String accountCode, int year, long periodId) {
        return jdbc.queryForObject("SELECT budgeted_amount FROM ims.budgets WHERE gl_account_id = ? AND fiscal_year = ? "
                + "AND period_id = ?", BigDecimal.class, glAccounts.idOf(accountCode), year, periodId);
    }

    private JournalHeaderDto postedJournal(LocalDate date, String debitCode, String creditCode, String amount) {
        BigDecimal value = new BigDecimal(amount);
        JournalHeaderDto draft = journals.createDraft(new JournalCreateRequest("GL", date, "Budget test " + System.nanoTime(),
                "INR", List.of(new JournalLineRequest(glAccounts.idOf(debitCode), value, BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(glAccounts.idOf(creditCode), BigDecimal.ZERO, value, "Cr"))));
        journals.submit(draft.id());
        td.runAsSecondUser(() -> journals.approve(draft.id()), "JOURNAL_APPROVE");
        return journals.post(draft.id());
    }

    private TrialBalanceRow trialBalanceRow(long periodId, String accountCode) {
        return journals.getTrialBalance(periodId).stream().filter(r -> accountCode.equals(r.accountCode()))
                .findFirst().orElseThrow(() -> new AssertionError("no trial balance row for " + accountCode));
    }

    private static BudgetVsActualRow row(BudgetVsActualDto report, String accountCode) {
        return report.rows().stream().filter(r -> accountCode.equals(r.accountCode())).findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + accountCode));
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "budget.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}

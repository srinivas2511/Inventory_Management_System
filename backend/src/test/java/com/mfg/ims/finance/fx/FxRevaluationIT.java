package com.mfg.ims.finance.fx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationDto;
import com.mfg.ims.finance.fx.dto.FxRevaluationDtos.FxRevaluationRunResult;
import com.mfg.ims.finance.fx.service.FxRevaluationService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Unrealised FX revaluation: open invoices in foreign currency are restated at the period closing
 * rate; the movement is an unrealised gain (4902) or loss (5209).
 * Non-transactional so each service call commits before the next one reads the result.
 */
@Transactional(propagation = Propagation.NEVER)
class FxRevaluationIT extends AbstractIntegrationTest {

    // 2012 fiscal year — FX-JAN-2012 at 80 INR/USD, FX-FEB-2012 at 82 INR/USD, FX-MAR at 78
    private static final LocalDate JAN_DATE = LocalDate.of(2012, 1, 15);
    private static final LocalDate JAN_END  = LocalDate.of(2012, 1, 31);

    @Autowired FxRevaluationService revalService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long supplierId;
    private Long customerId;
    private Long janPeriodId;
    private Long febPeriodId;
    private Long userId;

    @BeforeEach
    void setup() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        supplierId = td.supplier().getId();
        customerId = td.customer().getId();
        janPeriodId = jdbc.queryForObject(
                "SELECT id FROM ims.fiscal_periods WHERE period_name = 'FX-JAN-2012'", Long.class);
        febPeriodId = jdbc.queryForObject(
                "SELECT id FROM ims.fiscal_periods WHERE period_name = 'FX-FEB-2012'", Long.class);

        userId = jdbc.queryForObject(
                "INSERT INTO ims.users (username, full_name, email, password_hash) "
                        + "VALUES (?,?,?,'x') RETURNING id",
                Long.class, td.code("fxu"), "FX Test", td.code("fxu") + "@example.com");
        login(userId);
    }

    @AfterEach
    void cleanup() {
        // Delete revaluation rows for our test invoices (any currency — INR check uses FXAP-INR)
        jdbc.execute("DELETE FROM ims.fx_revaluations WHERE document_id IN "
                + "(SELECT id FROM ims.ap_invoices WHERE invoice_number LIKE 'FXAP%')");
        jdbc.execute("DELETE FROM ims.fx_revaluations WHERE document_id IN "
                + "(SELECT id FROM ims.ar_invoices WHERE invoice_number LIKE 'FXAR%')");
        jdbc.execute("DELETE FROM ims.ar_invoices WHERE invoice_number LIKE 'FXAR%'");
        jdbc.execute("DELETE FROM ims.ap_invoices WHERE invoice_number LIKE 'FXAP%'");
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("FXR-1: AP invoice in USD → INR weakened → unrealised loss Dr 5209 / Cr 2402")
    void apInvoiceUnrealisedLoss() {
        // 1 000 USD booked at 80 INR/USD = INR 80 000
        Long apId = seedApInvoice("FXAP-001", "1000.0000", "80.000000", "80000.0000");

        // revalue at FEB closing rate 82 INR/USD
        // remaining foreign = 1000, base carrying = 80000, revalued = 82000, diff = +2000 (loss)
        FxRevaluationRunResult result = revalService.run(febPeriodId);

        assertThat(result.apCount()).isGreaterThanOrEqualTo(1);
        // apLossInr covers all open USD AP invoices in the shared DB; assert ours is included
        assertThat(result.apLossInr()).isGreaterThanOrEqualTo(new BigDecimal("2000.0000"));

        // 2402 credited by 2000 and 5209 debited by 2000
        assertThat(lines(apId, "AP_INVOICE", febPeriodId)).containsExactlyInAnyOrder(
                "5209 Dr 2000", "2402 Cr 2000");

        // revaluation row saved
        List<FxRevaluationDto> rows = revalService.list(febPeriodId);
        FxRevaluationDto row = rows.stream()
                .filter(r -> "AP_INVOICE".equals(r.documentType()) && apId.equals(r.documentId()))
                .findFirst().orElseThrow();
        assertThat(row.differenceInr()).isEqualByComparingTo("2000.0000");
        assertThat(row.closingRate()).isEqualByComparingTo("82");
    }

    @Test
    @DisplayName("FXR-2: AR invoice in USD → INR weakened → unrealised gain Dr 1101 / Cr 4902")
    void arInvoiceUnrealisedGain() {
        // AR: a weaker INR means the receivable is worth MORE in INR → unrealised gain
        Long arId = seedArInvoice("FXAR-001", "500.0000", "80.000000", "40000.0000");

        FxRevaluationRunResult result = revalService.run(febPeriodId);

        assertThat(result.arCount()).isGreaterThanOrEqualTo(1);
        // remaining 500 USD * (82-80) = 1000 INR gain; may be higher if other tests left open USD AR invoices
        assertThat(result.arGainInr()).isGreaterThanOrEqualTo(new BigDecimal("1000.0000"));

        assertThat(arLines(arId, febPeriodId)).containsExactlyInAnyOrder(
                "1101 Dr 1000", "4902 Cr 1000");
    }

    @Test
    @DisplayName("FXR-3: INR-denominated invoices are not revalued")
    void inrInvoicesAreSkipped() {
        Long apId = seedApInvoice("FXAP-INR", "1000.0000", "1.000000", "1000.0000");
        // manually mark as INR currency (seed helper always uses USD, override via JDBC)
        jdbc.update("UPDATE ims.ap_invoices SET currency_code='INR' WHERE id=?", apId);

        FxRevaluationRunResult result = revalService.run(febPeriodId);

        List<FxRevaluationDto> rows = revalService.list(febPeriodId);
        // Filter by document type: AP and AR share separate BIGSERIAL sequences, so numeric IDs
        // can collide across tables in the shared Testcontainers DB.
        boolean found = rows.stream()
                .anyMatch(r -> "AP_INVOICE".equals(r.documentType()) && apId.equals(r.documentId()));
        assertThat(found).isFalse();
        // ap/ar counts may include other invoices seeded in other tests (shared Testcontainers DB)
        // so we just assert the INR invoice was not revalued rather than asserting count
    }

    @Test
    @DisplayName("FXR-4: partial payment reduces the open foreign balance subject to revaluation")
    void partialPaymentReducesRevaluationBase() {
        // 1 000 USD booked at 80; paid 400 USD → remaining 600 USD
        Long apId = seedApInvoice("FXAP-PART", "1000.0000", "80.000000", "80000.0000");
        // mark 400 paid and PARTIALLY_PAID (in the invoice currency)
        jdbc.update("UPDATE ims.ap_invoices SET paid_amount=400, status='PARTIALLY_PAID' WHERE id=?", apId);

        FxRevaluationRunResult result = revalService.run(febPeriodId);

        assertThat(result.apLossInr()).isGreaterThanOrEqualTo(new BigDecimal("1200.0000")); // 600 * 2 = 1200 for this invoice
        assertThat(lines(apId, "AP_INVOICE", febPeriodId)).containsExactlyInAnyOrder(
                "5209 Dr 1200", "2402 Cr 1200");
    }

    @Test
    @DisplayName("FXR-5: re-running after a rate change posts only the incremental adjustment")
    void rerunPostsIncrementalAdjustment() {
        Long apId = seedApInvoice("FXAP-RERUN", "1000.0000", "80.000000", "80000.0000");

        // first run at 82: diff = +2000
        revalService.run(febPeriodId);

        // update FEB rate to 83 and re-run
        jdbc.update("UPDATE ims.exchange_rates SET rate=83 WHERE from_currency='USD' "
                + "AND period_id=?", febPeriodId);

        FxRevaluationRunResult second = revalService.run(febPeriodId);

        // additional loss for this invoice = 1000 * (83-82) = 1000; cumulative diff becomes 3000
        assertThat(second.apLossInr()).isGreaterThanOrEqualTo(new BigDecimal("1000.0000"));
        List<FxRevaluationDto> rows = revalService.list(febPeriodId);
        FxRevaluationDto row = rows.stream()
                .filter(r -> "AP_INVOICE".equals(r.documentType()) && apId.equals(r.documentId()))
                .findFirst().orElseThrow();
        assertThat(row.differenceInr()).isEqualByComparingTo("3000.0000");
        assertThat(row.closingRate()).isEqualByComparingTo("83");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long seedApInvoice(String num, String totalForeign, String rate, String totalInr) {
        return jdbc.queryForObject("""
                INSERT INTO ims.ap_invoices
                  (invoice_number, supplier_id, invoice_date, due_date, subtotal, tax_total,
                   total_amount, paid_amount, debited_amount, status, currency_code, exchange_rate,
                   total_inr, period_id, goods_receipt_id, match_status, match_cleared_amount,
                   match_variance, price_variance_inr, company_id)
                VALUES (?,?,?::date,?::date,?,0,?,0,0,'OPEN','USD',?::numeric,?::numeric,?,NULL,
                        'NOT_REQUIRED',0,0,0,1)
                RETURNING id
                """, Long.class, num, supplierId, JAN_DATE, JAN_END,
                new BigDecimal(totalForeign), new BigDecimal(totalForeign),
                new BigDecimal(rate), new BigDecimal(totalInr), janPeriodId);
    }

    private Long seedArInvoice(String num, String totalForeign, String rate, String totalInr) {
        return jdbc.queryForObject("""
                INSERT INTO ims.ar_invoices
                  (invoice_number, customer_id, invoice_date, due_date, subtotal, tax_total,
                   total_amount, received_amount, credited_amount, status, currency_code,
                   exchange_rate, total_inr, period_id, company_id)
                VALUES (?,?,?::date,?::date,?,0,?,0,0,'OPEN','USD',?::numeric,?::numeric,?,1)
                RETURNING id
                """, Long.class, num, customerId, JAN_DATE, JAN_END,
                new BigDecimal(totalForeign), new BigDecimal(totalForeign),
                new BigDecimal(rate), new BigDecimal(totalInr), janPeriodId);
    }

    private List<String> lines(Long docId, String docType, Long periodId) {
        Long journalId = jdbc.queryForObject(
                "SELECT journal_header_id FROM ims.fx_revaluations "
                        + "WHERE document_type=? AND document_id=? AND period_id=?",
                Long.class, docType, docId, periodId);
        return journalLines(journalId);
    }

    private List<String> arLines(Long arId, Long periodId) {
        return lines(arId, "AR_INVOICE", periodId);
    }

    private List<String> journalLines(Long journalHeaderId) {
        return jdbc.query("""
                SELECT a.account_code, jl.debit_amount, jl.credit_amount
                  FROM ims.journal_lines jl
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jl.journal_header_id = ?
                """, (rs, i) -> {
            BigDecimal dr = rs.getBigDecimal("debit_amount");
            BigDecimal cr = rs.getBigDecimal("credit_amount");
            BigDecimal amt = dr.signum() > 0 ? dr : cr;
            String side = dr.signum() > 0 ? " Dr " : " Cr ";
            return rs.getString("account_code") + side
                    + amt.stripTrailingZeros().toPlainString();
        }, journalHeaderId);
    }

    private void login(Long userId) {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                  JOIN ims.role_permissions rp ON rp.permission_id = p.id
                  JOIN ims.roles r ON r.id = rp.role_id WHERE r.code = 'ADMIN'
                """, String.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CachedUser(userId, "u" + userId, true, 1, Set.of("ADMIN"), Set.copyOf(codes)),
                        null, codes.stream().map(SimpleGrantedAuthority::new).toList()));
    }

}

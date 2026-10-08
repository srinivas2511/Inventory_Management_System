package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.service.ApService;
import com.springmfg.ims.finance.config.dto.ExchangeRateRequest;
import com.springmfg.ims.finance.config.service.ExchangeRateService;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * R9-7.6 / FIN-009: foreign-currency supplier invoices. INR is the functional currency, so every journal is in INR; the
 * invoice keeps its currency and the rate of its own date, and a payment at another rate books the realised exchange
 * difference. Dates are fixed in 2012 (periods and rates created by the test) so the numbers never depend on the clock.
 * Not {@code @Transactional}: the goods receipt journal is posted after commit.
 */
class ForeignCurrencyIT extends AbstractIntegrationTest {

    private static final LocalDate INVOICE_DAY = LocalDate.of(2012, 1, 15);   // USD 80.00
    private static final LocalDate PAY_DAY = LocalDate.of(2012, 2, 10);       // USD 82.00
    private static final LocalDate LATER_PAY_DAY = LocalDate.of(2012, 3, 5);  // USD 78.00

    @Autowired ExchangeRateService rates;
    @Autowired ApService apService;
    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long clerk;
    private Long buyer;
    private Long approver;

    @BeforeEach
    void setUp() {
        td.foreignCurrencyPeriods("80.000000", "82.000000", "78.000000");
        clerk = newUser("clk");
        buyer = newUser("buy");
        approver = newUser("apr");
        login(clerk, "ACCOUNTANT");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ── rates ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FX-1: the rate is the one of the period holding the date; INR is always 1")
    void rateIsThePeriodsRate() {
        assertThat(rates.rateFor("INR", INVOICE_DAY)).isEqualByComparingTo("1");
        assertThat(rates.rateFor("USD", INVOICE_DAY)).isEqualByComparingTo("80");
        assertThat(rates.rateFor("USD", PAY_DAY)).isEqualByComparingTo("82");
        assertThat(rates.rateFor("USD", LATER_PAY_DAY)).isEqualByComparingTo("78");
        assertThat(ExchangeRateService.toInr(new BigDecimal("10.01"), new BigDecimal("83.333333")))
                .as("10.01 x 83.333333 = 834.16666633 -> 834.1667 half-up").isEqualByComparingTo("834.1667");
    }

    @Test
    @DisplayName("FX-2: a missing rate stops the document with a message that says how to fix it")
    void missingRateStopsTheDocument() {
        assertThatThrownBy(() -> rates.rateFor("CHF", INVOICE_DAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no CHF to INR exchange rate for period FX-JAN-2012")
                .hasMessageContaining("Exchange rates");
    }

    @Test
    @DisplayName("FX-3: a rate is set per period, replaces the old one, is audited, and INR cannot have one")
    void rateMaintenance() {
        Long periodId = jdbc.queryForObject("SELECT id FROM ims.fiscal_periods WHERE period_name = 'FX-JAN-2012'", Long.class);
        login(clerk, "FINANCE_MANAGER");

        var created = rates.save(new ExchangeRateRequest("EUR", periodId, new BigDecimal("90.5")));
        assertThat(created.rate()).isEqualByComparingTo("90.5");
        assertThat(rates.save(new ExchangeRateRequest("EUR", periodId, new BigDecimal("91.25"))).id()).isEqualTo(created.id());
        assertThat(rates.rateFor("EUR", INVOICE_DAY)).isEqualByComparingTo("91.25");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ims.audit_logs WHERE action = 'EXCHANGE_RATE_SET' AND entity_id = ?
                """, Integer.class, String.valueOf(created.id()))).isEqualTo(2);
        assertThatThrownBy(() -> rates.save(new ExchangeRateRequest("INR", periodId, BigDecimal.ONE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("functional currency");
    }

    // ── supplier invoices ──────────────────────────────────────────────────────

    @Test
    @DisplayName("FX-4: USD 250 at 80 matches a 20,000 receipt and books Dr GR/IR 20,000 / Cr AP 20,000 in INR")
    void usdInvoiceIsConvertedForTheMatchAndTheJournal() {
        Receipt gr = receipt();

        ApInvoiceDto draft = invoice(gr, "250");

        assertThat(draft.currencyCode()).isEqualTo("USD");
        assertThat(draft.exchangeRate()).isEqualByComparingTo("80");
        assertThat(draft.totalAmount()).as("the invoice stays in USD").isEqualByComparingTo("250");
        assertThat(draft.matchStatus()).isEqualTo("MATCHED");
        assertThat(draft.matchExpectedAmount()).isEqualByComparingTo("20000");

        ApInvoiceDto open = apService.openInvoice(draft.id());
        assertThat(open.totalInr()).isEqualByComparingTo("20000");
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder("2401 Dr 20000", "2402 Cr 20000");
    }

    @Test
    @DisplayName("FX-5: the same USD 250 is an exception when the rate is 90, because it is now worth 22,500 INR")
    void rateChangesTheMatch() {
        Long periodId = jdbc.queryForObject("SELECT id FROM ims.fiscal_periods WHERE period_name = 'FX-JAN-2012'", Long.class);
        Receipt gr = receipt();
        login(clerk, "FINANCE_MANAGER");
        rates.save(new ExchangeRateRequest("USD", periodId, new BigDecimal("90")));
        login(clerk, "ACCOUNTANT");

        ApInvoiceDto draft = invoice(gr, "250");

        assertThat(draft.matchStatus()).isEqualTo("EXCEPTION");
        assertThat(draft.matchMessage()).contains("22500.00").contains("20000.00");
    }

    @Test
    @DisplayName("FX-6: paying USD 250 at 82 against a payable carried at 80 books a realised loss of 500")
    void paymentAtAHigherRateBooksALoss() {
        ApInvoiceDto open = openUsd(receipt(), "250");
        BigDecimal loss = balance("5208");
        BigDecimal bank = balance("1201");

        ApInvoiceDto paid = apService.recordPayment(open.id(), new ApPaymentRequest(PAY_DAY, bd("250"), "BANK_TRANSFER", "FX-1"));

        assertThat(paid.status()).isEqualTo("PAID");
        var settlements = apService.payments(open.id());
        assertThat(settlements).hasSize(1);
        assertThat(settlements.get(0).amount()).isEqualByComparingTo("250");
        assertThat(settlements.get(0).exchangeRate()).as("the rate of the payment month").isEqualByComparingTo("82");
        assertThat(settlements.get(0).carryingInr()).as("what came off the payable").isEqualByComparingTo("20000");
        assertThat(settlements.get(0).fxDifference()).as("a loss is negative").isEqualByComparingTo("-500");
        Long journal = jdbc.queryForObject("""
                SELECT journal_header_id FROM ims.ap_payments WHERE ap_invoice_id = ?
                """, Long.class, open.id());
        assertThat(lines(journal)).containsExactlyInAnyOrder("2402 Dr 20000", "5208 Dr 500", "1201 Cr 20500");
        assertThat(balance("5208")).isEqualByComparingTo(loss.add(bd("500")));
        assertThat(balance("1201")).isEqualByComparingTo(bank.subtract(bd("20500")));
    }

    @Test
    @DisplayName("FX-7: paying at 78 books a realised gain of 500")
    void paymentAtALowerRateBooksAGain() {
        ApInvoiceDto open = openUsd(receipt(), "250");

        apService.recordPayment(open.id(), new ApPaymentRequest(LATER_PAY_DAY, bd("250"), "BANK_TRANSFER", "FX-2"));

        Long journal = jdbc.queryForObject("SELECT journal_header_id FROM ims.ap_payments WHERE ap_invoice_id = ?",
                Long.class, open.id());
        assertThat(lines(journal)).containsExactlyInAnyOrder("2402 Dr 20000", "1201 Cr 19500", "4901 Cr 500");
    }

    @Test
    @DisplayName("FX-8: partial payments of 100 and 150 at 82 lose 200 and 300; the payable ends at exactly zero")
    void partialPaymentsSettleExactly() {
        ApInvoiceDto open = openUsd(receipt(), "250");

        apService.recordPayment(open.id(), new ApPaymentRequest(PAY_DAY, bd("100"), "BANK_TRANSFER", "P1"));
        ApInvoiceDto paid = apService.recordPayment(open.id(), new ApPaymentRequest(PAY_DAY, bd("150"), "BANK_TRANSFER", "P2"));

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(jdbc.queryForList("""
                SELECT carrying_inr, fx_difference FROM ims.ap_payments WHERE ap_invoice_id = ? ORDER BY id
                """, open.id())).extracting(r -> ((BigDecimal) r.get("carrying_inr")).toPlainString() + "/"
                + ((BigDecimal) r.get("fx_difference")).toPlainString())
                .containsExactly("8000.0000/200.0000", "12000.0000/300.0000");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(jl.debit_amount - jl.credit_amount), 0)
                  FROM ims.journal_lines jl JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE a.account_code = '2402' AND jh.source_document_id = ? AND jh.source_document_type IN
                       ('SUPPLIER_INVOICE', 'SUPPLIER_PAYMENT')
                """, BigDecimal.class, open.id())).as("payable of this invoice nets to zero").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("FX-9: a supplier's currency is the default, an INR supplier still posts exactly as before")
    void supplierCurrencyIsTheDefaultAndInrIsUnchanged() {
        Receipt usd = receipt();
        assertThat(invoice(usd, "250").currencyCode()).isEqualTo("USD");

        Receipt inr = receiptFrom("INR", "0");
        ApInvoiceDto draft = apService.createInvoice(new ApInvoiceCreateRequest(inr.supplierId, td.code("SINV"),
                INVOICE_DAY, INVOICE_DAY.plusDays(30), inr.id, bd("20000"), bd("0")));
        assertThat(draft.currencyCode()).isEqualTo("INR");
        assertThat(draft.exchangeRate()).isEqualByComparingTo("1");
        ApInvoiceDto open = apService.openInvoice(draft.id());
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder("2401 Dr 20000", "2402 Cr 20000");
        ApInvoiceDto paid = apService.recordPayment(open.id(), new ApPaymentRequest(PAY_DAY, bd("20000"), "BANK_TRANSFER", "I1"));
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT fx_difference FROM ims.ap_payments WHERE ap_invoice_id = ?",
                BigDecimal.class, open.id())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("FX-10: USD 250 plus 18% GST (USD 45) books input tax converted at 80: 1,800 CGST and 1,800 SGST")
    void foreignInvoiceWithGst() {
        Receipt gr = receiptFrom("USD", "18");
        jdbc.update("UPDATE ims.suppliers SET gst_number = '27AAAAA0000A1Z5' WHERE id = ?", gr.supplierId);

        ApInvoiceDto draft = apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId, td.code("SINV"),
                INVOICE_DAY, INVOICE_DAY.plusDays(30), gr.id, bd("250"), null, "GST18"));

        assertThat(draft.taxTotal()).isEqualByComparingTo("45");
        assertThat(draft.totalAmount()).isEqualByComparingTo("295");
        ApInvoiceDto open = apService.openInvoice(draft.id());
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "1311 Dr 1800", "1312 Dr 1800", "2402 Cr 23600");
        assertThat(open.totalInr()).isEqualByComparingTo("23600");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private record Receipt(Long id, Long supplierId) {}

    private Receipt receipt() {
        return receiptFrom("USD", "0");
    }

    /** A 20,000 INR receipt (100 x 200) from a new supplier whose currency is the given one. */
    private Receipt receiptFrom(String currency, String poTaxPct) {
        Long supplierId = td.supplier().getId();
        jdbc.update("UPDATE ims.suppliers SET currency_code = ? WHERE id = ?", currency, supplierId);
        Long materialId = td.material().getId();
        login(buyer, "ADMIN");
        var po = poService.create(new CreatePoRequest(supplierId, LocalDate.now(), LocalDate.now().plusDays(7), "NET30",
                null, null, List.of(new CreatePoRequest.PoLineRequest(materialId, bd("100"), bd("200.0000"), bd(poTaxPct)))));
        poService.submit(po.id());
        login(approver, "ADMIN");
        po = poService.approve(po.id());
        Long poItemId = po.items().get(0).id();
        GoodsReceiptDto gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), 1L, "CH-" + po.id(),
                "MH01FX0001", List.of(new CreateGrRequest.GrLineRequest(poItemId, bd("100"), 1L,
                        "HEAT-" + po.id(), "CERT-" + po.id(), null, true))));
        login(clerk, "ACCOUNTANT");
        return new Receipt(gr.id(), supplierId);
    }

    private ApInvoiceDto invoice(Receipt gr, String subtotal) {
        return apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId, td.code("SINV"), INVOICE_DAY,
                INVOICE_DAY.plusDays(30), gr.id, bd(subtotal), bd("0")));
    }

    private ApInvoiceDto openUsd(Receipt gr, String subtotal) {
        return apService.openInvoice(invoice(gr, subtotal).id());
    }

    private List<String> lines(Long journalHeaderId) {
        return jdbc.query("""
                SELECT a.account_code, jl.debit_amount, jl.credit_amount
                  FROM ims.journal_lines jl JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jl.journal_header_id = ?
                """, (rs, i) -> {
            BigDecimal dr = rs.getBigDecimal("debit_amount");
            BigDecimal cr = rs.getBigDecimal("credit_amount");
            BigDecimal amount = dr.signum() > 0 ? dr : cr;
            return rs.getString("account_code") + (dr.signum() > 0 ? " Dr " : " Cr ")
                    + amount.stripTrailingZeros().toPlainString();
        }, journalHeaderId);
    }

    private BigDecimal balance(String accountCode) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(jl.debit_amount - jl.credit_amount), 0)
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id AND jh.status = 'POSTED'
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE a.account_code = ?
                """, BigDecimal.class, accountCode);
    }

    private Long newUser(String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'FX Test', ?, 'x') RETURNING id
                """, Long.class, td.code(prefix), td.code(prefix) + "@example.com");
    }

    private void login(Long userId, String roleCode) {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                  JOIN ims.role_permissions rp ON rp.permission_id = p.id
                  JOIN ims.roles r ON r.id = rp.role_id WHERE r.code = ?
                """, String.class, roleCode);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(roleCode), Set.copyOf(codes)), null,
                codes.stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}

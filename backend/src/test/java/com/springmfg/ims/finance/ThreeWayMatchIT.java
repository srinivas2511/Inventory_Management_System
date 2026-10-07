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
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.dto.ApPaymentRequest;
import com.springmfg.ims.finance.ap.service.ApService;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Business rule 18 / FIN-003: a supplier invoice must reconcile to its goods receipt before it can be opened or paid.
 * Deliberately NOT {@code @Transactional}: the goods receipt journal is posted after commit, and the invoice journal
 * has to clear exactly what that journal credited to GR/IR. Real users hold their role's seeded permissions.
 */
class ThreeWayMatchIT extends AbstractIntegrationTest {

    private static final Long WAREHOUSE_ID = 1L;
    private static final Long LOCATION_ID = 1L;
    private static final String TOLERANCE_KEY = "ap.match.price_tolerance_pct";

    @Autowired ApService apService;
    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired SystemSettingService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long clerk;
    private Long financeManager;
    private Long otherFinanceManager;
    private Long buyer;
    private Long approver;
    private Long adminOne;
    private Long adminTwo;

    @BeforeEach
    void users() {
        clerk = newUser("clk");
        financeManager = newUser("fm");
        otherFinanceManager = newUser("fm2");
        buyer = newUser("buy");
        approver = newUser("apr");
        adminOne = newUser("adm");
        adminTwo = newUser("adm2");
        login(clerk, "ACCOUNTANT");
    }

    @AfterEach
    void clear() {
        settings.update(TOLERANCE_KEY, "0");
        SecurityContextHolder.clearContext();
    }

    // ── scenarios ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TWM-1: an invoice equal to the receipt matches, opens, and clears GR/IR exactly")
    void matchedInvoiceOpensAndClearsGrIr() {
        BigDecimal grIr = balance("2401");
        Receipt gr = receipt("100", "200.0000", null, "100");   // 20,000
        BigDecimal ap = balance("2402");

        ApInvoiceDto draft = invoice(gr, "20000.0000", "0");
        assertThat(draft.matchStatus()).isEqualTo("MATCHED");
        assertThat(draft.matchExpectedAmount()).isEqualByComparingTo("20000");
        assertThat(draft.matchVarianceAmount()).isEqualByComparingTo("0");

        ApInvoiceDto open = apService.openInvoice(draft.id());

        assertThat(open.status()).isEqualTo("OPEN");
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "2402 Cr 20000");
        // the receipt credited 20,000 to GR/IR and the invoice debited 20,000: net nothing, in a balanced ledger
        assertThat(balance("2401")).isEqualByComparingTo(grIr);
        assertThat(balance("2402")).isEqualByComparingTo(ap.subtract(bd("20000")));
    }

    @Test
    @DisplayName("TWM-2: 25,000 against a 20,000 receipt is an exception that cannot be opened or paid")
    void overInvoicedIsAnExceptionAndIsBlocked() {
        Receipt gr = receipt("100", "200.0000", null, "100");

        ApInvoiceDto exception = invoice(gr, "25000.0000", "0");

        assertThat(exception.matchStatus()).isEqualTo("EXCEPTION");
        assertThat(exception.status()).isEqualTo("DRAFT");
        assertThat(exception.matchMessage()).contains("25000.00").contains("20000.00").contains("AP_MATCH_OVERRIDE");
        assertThatThrownBy(() -> apService.openInvoice(exception.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match its goods receipt")
                .hasMessageContaining("25000.00");
        assertThatThrownBy(() -> apService.recordPayment(exception.id(),
                new ApPaymentRequest(LocalDate.now(), bd("1000"), "BANK_TRANSFER", "PAY-X")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM ims.ap_invoices WHERE id = ?", String.class, exception.id()))
                .isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("TWM-3: within the configured 2% tolerance, 20,300 matches and 300 is booked as price variance")
    void withinToleranceBooksPriceVariance() {
        settings.update(TOLERANCE_KEY, "2");
        Receipt gr = receipt("100", "200.0000", null, "100");
        BigDecimal ppv = balance("5207");

        ApInvoiceDto draft = invoice(gr, "20300.0000", "0");
        assertThat(draft.matchStatus()).isEqualTo("MATCHED");
        assertThat(draft.matchVarianceAmount()).isEqualByComparingTo("300");
        ApInvoiceDto open = apService.openInvoice(draft.id());

        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "5207 Dr 300", "2402 Cr 20300");
        assertThat(balance("5207")).isEqualByComparingTo(ppv.add(bd("300")));
    }

    @Test
    @DisplayName("TWM-4: a finance manager approves the exception with a reason: audited, variance 5,000, GR/IR clears")
    void financeManagerOverridesWithAuditedReason() {
        BigDecimal grIr = balance("2401");
        Receipt gr = receipt("100", "200.0000", null, "100");
        BigDecimal ppv = balance("5207");
        ApInvoiceDto exception = invoice(gr, "25000.0000", "0");

        login(financeManager, "FINANCE_MANAGER");
        ApInvoiceDto approved = apService.overrideMatch(exception.id(), "Supplier confirmed a rate revision by phone");

        assertThat(approved.matchStatus()).isEqualTo("OVERRIDDEN");
        assertThat(approved.matchVarianceAmount()).isEqualByComparingTo("5000");
        assertThat(approved.status()).isEqualTo("DRAFT");
        var audit = jdbc.queryForMap("""
                SELECT user_id, old_value::text AS old_value, new_value::text AS new_value, reason
                  FROM ims.audit_logs WHERE action = 'AP_MATCH_OVERRIDE' AND entity = 'AP_INVOICE' AND entity_id = ?
                """, String.valueOf(exception.id()));
        assertThat(audit.get("user_id")).isEqualTo(financeManager);
        assertThat(String.valueOf(audit.get("old_value"))).contains("EXCEPTION");
        assertThat(String.valueOf(audit.get("new_value"))).contains("OVERRIDDEN").contains("5000");
        assertThat(audit.get("reason")).isEqualTo("Supplier confirmed a rate revision by phone");

        ApInvoiceDto open = apService.openInvoice(exception.id());
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "5207 Dr 5000", "2402 Cr 25000");
        assertThat(balance("2401")).isEqualByComparingTo(grIr);
        assertThat(balance("5207")).isEqualByComparingTo(ppv.add(bd("5000")));
    }

    @Test
    @DisplayName("TWM-5: nobody approves their own invoice, administrators included; a reason is mandatory")
    void segregationOfDutiesOnTheOverride() {
        Receipt gr = receipt("100", "200.0000", null, "100");
        Receipt gr2 = receipt("100", "200.0000", null, "100");
        Receipt gr3 = receipt("100", "200.0000", null, "100");

        // a finance manager who entered the invoice cannot approve it; another one can
        login(financeManager, "FINANCE_MANAGER");
        ApInvoiceDto byFinanceManager = invoice(gr, "25000.0000", "0");
        assertThatThrownBy(() -> apService.overrideMatch(byFinanceManager.id(), "mine"))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);

        // an administrator holds the permission but not the right to approve their own entry
        login(adminOne, "ADMIN");
        ApInvoiceDto byAdmin = invoice(gr2, "26000.0000", "0");
        assertThatThrownBy(() -> apService.overrideMatch(byAdmin.id(), "mine"))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);
        assertThat(jdbc.queryForObject("SELECT match_status FROM ims.ap_invoices WHERE id = ?", String.class, byAdmin.id()))
                .isEqualTo("EXCEPTION");

        login(adminTwo, "ADMIN");
        assertThatThrownBy(() -> apService.overrideMatch(byAdmin.id(), "   "))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(apService.overrideMatch(byAdmin.id(), "Approved by the other administrator").matchStatus())
                .isEqualTo("OVERRIDDEN");
        login(otherFinanceManager, "FINANCE_MANAGER");
        assertThat(apService.overrideMatch(byFinanceManager.id(), "Approved by the other finance manager").matchStatus())
                .isEqualTo("OVERRIDDEN");

        // a matched invoice has nothing to approve
        login(clerk, "ACCOUNTANT");
        ApInvoiceDto fine = invoice(gr3, "20000.0000", "0");
        login(financeManager, "FINANCE_MANAGER");
        assertThatThrownBy(() -> apService.overrideMatch(fine.id(), "not needed"))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    @DisplayName("TWM-6: a receipt that is already fully invoiced cannot be invoiced again")
    void secondInvoiceForAnInvoicedReceiptIsRejected() {
        Receipt gr = receipt("100", "200.0000", null, "100");
        assertThat(invoice(gr, "20000.0000", "0").matchStatus()).isEqualTo("MATCHED");

        ApInvoiceDto second = invoice(gr, "20000.0000", "0");

        assertThat(second.matchStatus()).isEqualTo("EXCEPTION");
        assertThat(second.matchMessage()).contains("already fully invoiced");
    }

    @Test
    @DisplayName("TWM-7: two partial invoices (12,000 + 8,000) that sum to the receipt pass, a third does not")
    void partialInvoicesSumToTheReceipt() {
        Receipt gr = receipt("100", "200.0000", null, "100");

        ApInvoiceDto first = invoice(gr, "12000.0000", "0");
        ApInvoiceDto second = invoice(gr, "8000.0000", "0");
        ApInvoiceDto third = invoice(gr, "100.0000", "0");

        assertThat(first.matchStatus()).isEqualTo("MATCHED");
        assertThat(second.matchStatus()).isEqualTo("MATCHED");
        assertThat(second.matchExpectedAmount()).isEqualByComparingTo("8000");
        assertThat(third.matchStatus()).isEqualTo("EXCEPTION");
    }

    @Test
    @DisplayName("TWM-8: partial receipts are matched per receipt (PO 100, receipts of 40 and 60)")
    void partialReceiptsMatchPerReceipt() {
        Receipt first = receipt("100", "200.0000", null, "40");           // 8,000
        Receipt second = receiptAgainst(first.poId, first.poItemId, "60"); // 12,000

        assertThat(invoice(first, "8000.0000", "0").matchStatus()).isEqualTo("MATCHED");
        assertThat(invoice(second, "12000.0000", "0").matchStatus()).isEqualTo("MATCHED");
        // the first receipt is worth 8,000 only: 12,000 against it is an exception
        assertThat(invoice(first, "12000.0000", "0").matchStatus()).isEqualTo("EXCEPTION");
    }

    @Test
    @DisplayName("TWM-9: cancelling a draft invoice frees its goods receipt to be invoiced again")
    void cancelledInvoiceFreesTheReceipt() {
        Receipt gr = receipt("100", "200.0000", null, "100");
        ApInvoiceDto wrong = invoice(gr, "20000.0000", "0");
        assertThat(invoice(gr, "20000.0000", "0").matchStatus()).isEqualTo("EXCEPTION");

        assertThat(apService.cancelInvoice(wrong.id()).status()).isEqualTo("CANCELLED");

        assertThat(invoice(gr, "20000.0000", "0").matchStatus()).isEqualTo("MATCHED");
        // an open invoice cannot simply be cancelled
        ApInvoiceDto open = apService.openInvoice(invoice(receipt("10", "100.0000", null, "10"), "1000.0000", "0").id());
        assertThatThrownBy(() -> apService.cancelInvoice(open.id()))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    @DisplayName("TWM-10: invoice tax is matched against the PO rate and posted to input tax, not to GR/IR")
    void taxIsMatchedAndPostedToInputTax() {
        Receipt gr = receipt("100", "200.0000", "18.00", "100");   // 20,000 + 3,600 tax

        ApInvoiceDto bad = invoice(gr, "20000.0000", "5000");
        assertThat(bad.matchStatus()).isEqualTo("EXCEPTION");
        assertThat(bad.matchMessage()).contains("5000.00").contains("3600.00");
        apService.cancelInvoice(bad.id());

        ApInvoiceDto good = invoice(gr, "20000.0000", "3600");
        assertThat(good.matchStatus()).isEqualTo("MATCHED");
        ApInvoiceDto open = apService.openInvoice(good.id());
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "1301 Dr 3600", "2402 Cr 23600");
    }

    @Test
    @DisplayName("TWM-11: an invoice must name the goods receipt, belong to the same supplier, and sit in an open period")
    void basicInvoiceRules() {
        Receipt gr = receipt("100", "200.0000", null, "100");

        assertThatThrownBy(() -> apService.createInvoice(new ApInvoiceCreateRequest(
                gr.supplierId, "S-1", LocalDate.now(), LocalDate.now().plusDays(30), null, bd("100"), BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);
        Long otherSupplier = td.supplier().getId();
        assertThatThrownBy(() -> apService.createInvoice(new ApInvoiceCreateRequest(
                otherSupplier, "S-2", LocalDate.now(), LocalDate.now().plusDays(30), gr.id, bd("100"), BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different supplier");
        assertThatThrownBy(() -> apService.createInvoice(new ApInvoiceCreateRequest(
                gr.supplierId, "S-3", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 2, 1), gr.id, bd("100"), BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.PERIOD_CLOSED);
    }

    @Test
    @DisplayName("Business Rule 19: AP invoice creation generates a valid DRAFT invoice against its goods receipt")
    void apInvoiceCreationGeneratesDraft() {
        Receipt gr = receipt("100", "100.0000", "18.00", "100");   // 10,000 + 1,800 tax

        ApInvoiceDto dto = invoice(gr, "10000.0000", "1800.0000");

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.matchStatus()).isEqualTo("MATCHED");
        assertThat(dto.totalAmount()).isEqualByComparingTo("11800.0000");
        assertThat(dto.paidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.balanceAmount()).isEqualByComparingTo("11800.0000");
    }

    @Test
    @DisplayName("AP-01: a partial supplier payment leaves the invoice PARTIALLY_PAID, the rest makes it PAID")
    void apPartialPayment_thenFull() {
        Receipt gr = receipt("10", "100.0000", null, "10");   // 1,000
        ApInvoiceDto open = apService.openInvoice(invoice(gr, "1000.0000", "0").id());
        assertThat(open.status()).isEqualTo("OPEN");

        ApInvoiceDto afterPartial = apService.recordPayment(open.id(),
                new ApPaymentRequest(LocalDate.now(), bd("300.0000"), "BANK_TRANSFER", "PAY-1"));
        assertThat(afterPartial.status()).isEqualTo("PARTIALLY_PAID");

        ApInvoiceDto afterFull = apService.recordPayment(open.id(),
                new ApPaymentRequest(LocalDate.now(), bd("700.0000"), "BANK_TRANSFER", "PAY-2"));
        assertThat(afterFull.status()).isEqualTo("PAID");
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private record Receipt(Long id, Long poId, Long poItemId, Long supplierId) {}

    /** A purchase order for {@code qty} at {@code rate}, approved by a second user, and a goods receipt of {@code received}. */
    private Receipt receipt(String qty, String rate, String taxPct, String received) {
        Long supplierId = td.supplier().getId();
        Long materialId = td.material().getId();
        login(buyer, "ADMIN");
        var po = poService.create(new CreatePoRequest(supplierId, LocalDate.now(), LocalDate.now().plusDays(7), "NET30",
                null, null, List.of(new CreatePoRequest.PoLineRequest(materialId, bd(qty), bd(rate),
                        taxPct == null ? BigDecimal.ZERO : bd(taxPct)))));
        poService.submit(po.id());
        login(approver, "ADMIN");
        po = poService.approve(po.id());
        Long poItemId = po.items().get(0).id();
        GoodsReceiptDto gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), WAREHOUSE_ID, "CH-" + po.id(),
                "MH01TW0001", List.of(new CreateGrRequest.GrLineRequest(poItemId, bd(received), LOCATION_ID,
                        "HEAT-" + po.id(), "CERT-" + po.id(), null, true))));
        login(clerk, "ACCOUNTANT");
        return new Receipt(gr.id(), po.id(), poItemId, supplierId);
    }

    private Receipt receiptAgainst(Long poId, Long poItemId, String received) {
        Long supplierId = jdbc.queryForObject("SELECT supplier_id FROM ims.purchase_orders WHERE id = ?", Long.class, poId);
        GoodsReceiptDto gr = grService.receive(new CreateGrRequest(poId, LocalDate.now(), WAREHOUSE_ID, "CH2-" + poId,
                "MH01TW0002", List.of(new CreateGrRequest.GrLineRequest(poItemId, bd(received), LOCATION_ID,
                        "HEAT2-" + poId, "CERT2-" + poId, null, true))));
        return new Receipt(gr.id(), poId, poItemId, supplierId);
    }

    private ApInvoiceDto invoice(Receipt gr, String subtotal, String tax) {
        return apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId, td.code("SINV"), LocalDate.now(),
                LocalDate.now().plusDays(30), gr.id, bd(subtotal), bd(tax)));
    }

    /** One journal's lines as "2401 Dr 20000": account, side and the amount without trailing zeros. */
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

    /** Net debit balance of a GL account over all posted journals. */
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
                VALUES (?, 'Match Test', ?, 'x') RETURNING id
                """, Long.class, td.code(prefix), td.code(prefix) + "@example.com");
    }

    /** Logs in with exactly the permissions the seeded role holds. */
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

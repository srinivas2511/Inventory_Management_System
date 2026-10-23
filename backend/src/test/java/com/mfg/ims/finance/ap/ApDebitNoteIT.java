package com.mfg.ims.finance.ap;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.ApDebitNoteDto;
import com.mfg.ims.finance.ap.dto.ApDebitNoteDtos.CreateDebitNoteRequest;
import com.mfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.mfg.ims.finance.ap.dto.ApInvoiceDto;
import com.mfg.ims.finance.ap.service.ApDebitNoteService;
import com.mfg.ims.finance.ap.service.ApService;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.GoodsReceiptDto;
import com.mfg.ims.procurement.service.GoodsReceiptService;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * AP debit notes: reduce the amount owed on a supplier invoice. Non-transactional so that each
 * service call commits before the next one reads the result — exactly as a real user session does.
 */
@Transactional(propagation = Propagation.NEVER)
class ApDebitNoteIT extends AbstractIntegrationTest {

    @Autowired ApDebitNoteService debitNoteService;
    @Autowired ApService apService;
    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long apClerk;
    private Long financeManager;
    private Long otherFinanceManager;
    private Long grId;
    private Long supplierId;

    @BeforeEach
    void setup() {
        apClerk = newUser("dn_clerk");
        financeManager = newUser("dn_fm");
        otherFinanceManager = newUser("dn_fm2");
        supplierId = td.supplier().getId();

        // Purchase order + goods receipt that the invoice will be matched against
        Long matId = td.material().getId();
        login(apClerk, "ADMIN");
        var po = poService.create(new CreatePoRequest(supplierId, LocalDate.now(),
                LocalDate.now().plusDays(30), null, null, null,
                List.of(new CreatePoRequest.PoLineRequest(matId, bd("100"), bd("200.00"), BigDecimal.ZERO))));
        poService.submit(po.id());
        login(financeManager, "ADMIN");
        po = poService.approve(po.id());
        Long poItemId = po.items().get(0).id();
        login(apClerk, "ADMIN");
        GoodsReceiptDto gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), 1L, "CH-DN", "VH-DN",
                List.of(new CreateGrRequest.GrLineRequest(poItemId, bd("100"), 1L,
                        "HT-DN", "CERT-DN", null, true))));
        grId = gr.id();
    }

    @AfterEach
    void cleanup() {
        if (supplierId != null) {
            jdbc.execute("DELETE FROM ims.ap_debit_notes WHERE debit_note_number LIKE 'APDN%'");
            jdbc.execute("UPDATE ims.ap_invoices SET status='DRAFT', debited_amount=0 WHERE status IN ('OPEN','PARTIALLY_PAID','PAID') AND supplier_id=" + supplierId);
        }
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DN-1: creating a debit note on an open invoice produces a DRAFT")
    void createDebitNoteOnOpenInvoice() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("20000.0000", "0");

        ApDebitNoteDto note = debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("500.00"), "Overcharge on item code X"));

        assertThat(note.status()).isEqualTo("DRAFT");
        assertThat(note.debitNoteNumber()).startsWith("APDN");
        assertThat(note.subtotal()).isEqualByComparingTo("500.00");
        assertThat(note.taxTotal()).isEqualByComparingTo("0");
        assertThat(note.totalAmount()).isEqualByComparingTo("500.00");
        assertThat(note.apInvoiceId()).isEqualTo(invoice.id());
    }

    @Test
    @DisplayName("DN-2: approving by a different user posts the debit and debits AP control")
    void approvePostsJournalAndUpdatesInvoice() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("20000.0000", "0");

        ApDebitNoteDto draft = debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("1000.00"), "Supplier agreed rebate"));

        // a different finance user approves it
        login(financeManager, "ADMIN");
        BigDecimal ap = balance("2402");
        BigDecimal ppv = balance("5207");

        ApDebitNoteDto posted = debitNoteService.approve(draft.id());

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.journalHeaderId()).isNotNull();
        assertThat(lines(posted.journalHeaderId())).containsExactlyInAnyOrder(
                "2402 Dr 1000", "5207 Cr 1000");
        // 2402 is a credit-normal account; debiting it by 1000 raises its debit-minus-credit balance
        assertThat(balance("2402")).isEqualByComparingTo(ap.add(bd("1000")));
        // 5207 is a debit-normal account; crediting it by 1000 lowers its debit-minus-credit balance
        assertThat(balance("5207")).isEqualByComparingTo(ppv.subtract(bd("1000")));

        // invoice debitedAmount is updated; balance now 19,000
        ApInvoiceDto updatedInv = apService.findAll(org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(i -> i.id().equals(invoice.id())).findFirst().orElseThrow();
        assertThat(updatedInv.debitedAmount()).isEqualByComparingTo("1000.00");
        assertThat(updatedInv.balanceAmount()).isEqualByComparingTo("19000.0000");
        assertThat(updatedInv.status()).isEqualTo("PARTIALLY_PAID");
    }

    @Test
    @DisplayName("DN-3: creator cannot approve their own debit note — SoD enforced")
    void creatorCannotApproveOwnNote() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("20000.0000", "0");
        ApDebitNoteDto draft = debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("500.00"), "SoD test"));

        assertThatThrownBy(() -> debitNoteService.approve(draft.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different");
    }

    @Test
    @DisplayName("DN-4: cancel returns the debit note to CANCELLED (no journal)")
    void cancelDraftDebitNote() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("20000.0000", "0");
        ApDebitNoteDto draft = debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("500.00"), "Cancellation test"));

        ApDebitNoteDto cancelled = debitNoteService.cancel(draft.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.journalHeaderId()).isNull();
    }

    @Test
    @DisplayName("DN-5: debit note cannot exceed the open balance")
    void debitNoteExceedsBalanceIsRejected() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("20000.0000", "0");

        assertThatThrownBy(() -> debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("25000.00"), "Exceeds balance")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("DN-6: full debit note sets the invoice status to PAID")
    void fullDebitNoteClosesInvoice() {
        login(apClerk, "ADMIN");
        ApInvoiceDto invoice = openInvoice("500.0000", "0");
        ApDebitNoteDto draft = debitNoteService.create(new CreateDebitNoteRequest(
                invoice.id(), LocalDate.now(), bd("500.00"), "Full overcharge reversal"));

        login(financeManager, "ADMIN");
        debitNoteService.approve(draft.id());

        ApInvoiceDto closed = apService.findAll(org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().filter(i -> i.id().equals(invoice.id())).findFirst().orElseThrow();
        assertThat(closed.status()).isEqualTo("PAID");
        assertThat(closed.balanceAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("DN-7: cannot raise a debit note on a DRAFT invoice")
    void draftInvoiceCannotBeDebited() {
        login(apClerk, "ADMIN");
        ApInvoiceDto draft = apService.createInvoice(new ApInvoiceCreateRequest(
                supplierId, td.code("SINV"), LocalDate.now(), LocalDate.now().plusDays(30),
                grId, bd("20000.0000"), bd("0")));

        assertThatThrownBy(() -> debitNoteService.create(new CreateDebitNoteRequest(
                draft.id(), LocalDate.now(), bd("500.00"), "Draft invoice")))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private ApInvoiceDto openInvoice(String subtotal, String tax) {
        var draft = apService.createInvoice(new ApInvoiceCreateRequest(
                supplierId, td.code("SINV"), LocalDate.now(), LocalDate.now().plusDays(30),
                grId, bd(subtotal), bd(tax)));
        return apService.openInvoice(draft.id());
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
                VALUES (?, 'Debit Note Test', ?, 'x') RETURNING id
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

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}

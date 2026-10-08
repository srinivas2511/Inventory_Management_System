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
import com.springmfg.ims.finance.ap.service.ApService;
import com.springmfg.ims.finance.tax.service.TaxAdminService;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Determination;
import com.springmfg.ims.finance.tax.service.TaxDeterminationService.Direction;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * R9-7.5 / FIN-007: GST is determined from data (slab x supply type -> tax codes), by the state in the GSTINs of the
 * company and the partner. The seeded company GSTIN is 27... (Maharashtra). Not {@code @Transactional}, like the other
 * finance ITs: the goods receipt journal is posted after commit.
 */
class GstTaxIT extends AbstractIntegrationTest {

    private static final String MAHARASHTRA = "27AAAAA0000A1Z5";
    private static final String KARNATAKA = "29AAAAA0000A1Z5";
    private static final String CHANDIGARH = "04BBBBB0000B1Z5";

    @Autowired TaxDeterminationService tax;
    @Autowired TaxAdminService taxAdmin;
    @Autowired ApService apService;
    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private String companyGstin;
    private Long clerk;
    private Long buyer;
    private Long approver;

    @BeforeEach
    void setUp() {
        companyGstin = jdbc.queryForObject("SELECT gstin FROM ims.companies WHERE id = 1", String.class);
        clerk = newUser("clk");
        buyer = newUser("buy");
        approver = newUser("apr");
        login(clerk, "ACCOUNTANT");
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE ims.companies SET gstin = ? WHERE id = 1", companyGstin);
        SecurityContextHolder.clearContext();
    }

    // ── determination ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GST-1: 18% on 20,000 within the company state splits into CGST 1,800 and SGST 1,800")
    void intraStateSplitsIntoCgstAndSgst() {
        Determination d = tax.determine(Direction.PURCHASE, "GST18", MAHARASHTRA, bd("20000"));

        assertThat(d.supplyType()).isEqualTo("INTRA");
        assertThat(d.partnerStateAssumed()).isFalse();
        assertThat(d.taxTotal()).isEqualByComparingTo("3600");
        assertThat(d.components()).extracting(c -> c.taxType() + " " + c.taxAmount().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("CGST 1800", "SGST 1800");
        // purchases post to the input tax accounts
        assertThat(d.components()).extracting(c -> c.glAccountId())
                .containsExactlyInAnyOrder(account("1311"), account("1312"));
    }

    @Test
    @DisplayName("GST-2: 18% on 20,000 to another state is IGST 3,600 on the output IGST account for a sale")
    void interStateIsIgst() {
        Determination d = tax.determine(Direction.SALES, "GST18", KARNATAKA, bd("20000"));

        assertThat(d.supplyType()).isEqualTo("INTER");
        assertThat(d.components()).hasSize(1);
        assertThat(d.components().get(0).taxType()).isEqualTo("IGST");
        assertThat(d.taxTotal()).isEqualByComparingTo("3600");
        assertThat(d.components().get(0).glAccountId()).isEqualTo(account("2313"));
    }

    @Test
    @DisplayName("GST-3: a supply inside a union territory is CGST + UTGST")
    void unionTerritorySplitsIntoCgstAndUtgst() {
        jdbc.update("UPDATE ims.companies SET gstin = ? WHERE id = 1", "04AABCS1234A1Z5");

        Determination d = tax.determine(Direction.SALES, "GST18", CHANDIGARH, bd("20000"));

        assertThat(d.supplyType()).isEqualTo("UT");
        assertThat(d.components()).extracting(c -> c.taxType() + " " + c.taxAmount().stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("CGST 1800", "UTGST 1800");
    }

    @Test
    @DisplayName("GST-4: an unregistered partner is assumed to be in the company state, and the result says so")
    void unregisteredPartnerIsAssumedIntraState() {
        Determination d = tax.determine(Direction.SALES, "GST12", null, bd("20000"));

        assertThat(d.partnerStateAssumed()).isTrue();
        assertThat(d.supplyType()).isEqualTo("INTRA");
        assertThat(d.taxTotal()).isEqualByComparingTo("2400");
        assertThat(d.components()).extracting(c -> c.taxType()).containsExactlyInAnyOrder("CGST", "SGST");
    }

    @Test
    @DisplayName("GST-5: the 5% slab splits 2.5% each, and each component rounds half-up to 4 decimals")
    void componentsRoundHalfUp() {
        // 10.01 x 2.5% = 0.25025 -> 0.2503 on each component
        Determination d = tax.determine(Direction.PURCHASE, "GST5", MAHARASHTRA, bd("10.01"));

        assertThat(d.components()).extracting(c -> c.taxAmount()).allSatisfy(a -> assertThat(a).isEqualByComparingTo("0.2503"));
        assertThat(d.taxTotal()).isEqualByComparingTo("0.5006");
        assertThat(tax.determine(Direction.PURCHASE, "GST5", KARNATAKA, bd("20000")).taxTotal()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("GST-6: a nil-rated slab taxes nothing and an unknown slab is refused")
    void nilRatedAndUnknownSlab() {
        Determination nil = tax.determine(Direction.PURCHASE, "GST0", MAHARASHTRA, bd("20000"));
        assertThat(nil.taxTotal()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> tax.determine(Direction.PURCHASE, "GST99", MAHARASHTRA, bd("1")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown tax slab");
    }

    // ── purchase invoices ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GST-7: an intra-state supplier invoice books input CGST and SGST: Dr GR/IR 20,000, 1311 1,800, 1312 1,800 / Cr AP 23,600")
    void apInvoiceIntraStateBooksInputCgstSgst() {
        Receipt gr = receipt(MAHARASHTRA);

        ApInvoiceDto draft = apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId, td.code("SINV"),
                LocalDate.now(), LocalDate.now().plusDays(30), gr.id, bd("20000"), null, "GST18"));

        assertThat(draft.matchStatus()).isEqualTo("MATCHED");
        assertThat(draft.taxTotal()).isEqualByComparingTo("3600");
        assertThat(draft.totalAmount()).isEqualByComparingTo("23600");
        assertThat(draft.supplyType()).isEqualTo("INTRA");
        assertThat(draft.taxSlabCode()).isEqualTo("GST18");
        assertThat(draft.taxLines()).extracting(l -> l.taxCode()).containsExactlyInAnyOrder("CGST9", "SGST9");

        ApInvoiceDto open = apService.openInvoice(draft.id());
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "1311 Dr 1800", "1312 Dr 1800", "2402 Cr 23600");
    }

    @Test
    @DisplayName("GST-8: an inter-state supplier invoice books input IGST 3,600")
    void apInvoiceInterStateBooksInputIgst() {
        Receipt gr = receipt(KARNATAKA);

        ApInvoiceDto open = apService.openInvoice(apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId,
                td.code("SINV"), LocalDate.now(), LocalDate.now().plusDays(30), gr.id, bd("20000"), null, "GST18")).id());

        assertThat(open.supplyType()).isEqualTo("INTER");
        assertThat(lines(open.journalHeaderId())).containsExactlyInAnyOrder(
                "2401 Dr 20000", "1313 Dr 3600", "2402 Cr 23600");
    }

    @Test
    @DisplayName("GST-9: a slab and a typed tax amount together are refused, because one would silently win")
    void slabAndAmountTogetherAreRefused() {
        Receipt gr = receipt(MAHARASHTRA);

        assertThatThrownBy(() -> apService.createInvoice(new ApInvoiceCreateRequest(gr.supplierId, td.code("SINV"),
                LocalDate.now(), LocalDate.now().plusDays(30), gr.id, bd("20000"), bd("100"), "GST18")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("either a tax slab or a tax amount");
    }

    @Test
    @DisplayName("GST-10: the preview for a supplier and the slab list come from the same data")
    void previewAndSlabList() {
        Receipt gr = receipt(KARNATAKA);

        var preview = taxAdmin.preview(Direction.PURCHASE, "GST18", gr.supplierId, bd("1000"));
        assertThat(preview.supplyType()).isEqualTo("INTER");
        assertThat(preview.taxTotal()).isEqualByComparingTo("180");

        assertThat(taxAdmin.listSlabs()).extracting(s -> s.slabCode()).contains("GST18", "GST12", "GST5", "GST0");
    }

    @Test
    @DisplayName("GST-11: a product's slab can be assigned, replaced and cleared; an unknown slab is refused")
    void productSlabAssignment() {
        Long productId = td.product().getId();
        login(clerk, "FINANCE_MANAGER");

        assertThat(taxAdmin.productSlab(productId).slabCode()).isNull();
        assertThat(taxAdmin.assignProductSlab(productId, "GST18").slabCode()).isEqualTo("GST18");
        assertThat(taxAdmin.assignProductSlab(productId, "GST12").slabCode()).isEqualTo("GST12");
        assertThat(taxAdmin.productSlab(productId).slabCode()).isEqualTo("GST12");
        assertThatThrownBy(() -> taxAdmin.assignProductSlab(productId, "NOPE"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(taxAdmin.assignProductSlab(productId, null).slabCode()).isNull();
        assertThat(taxAdmin.productSlab(productId).slabCode()).isNull();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private record Receipt(Long id, Long supplierId) {}

    /** A 20,000 receipt (100 x 200) from a supplier registered under the given GSTIN, against a PO taxed at 18%. */
    private Receipt receipt(String supplierGstin) {
        Long supplierId = td.supplier().getId();
        jdbc.update("UPDATE ims.suppliers SET gst_number = ? WHERE id = ?", supplierGstin, supplierId);
        Long materialId = td.material().getId();
        login(buyer, "ADMIN");
        var po = poService.create(new CreatePoRequest(supplierId, LocalDate.now(), LocalDate.now().plusDays(7), "NET30",
                null, null, List.of(new CreatePoRequest.PoLineRequest(materialId, bd("100"), bd("200.0000"), bd("18")))));
        poService.submit(po.id());
        login(approver, "ADMIN");
        po = poService.approve(po.id());
        Long poItemId = po.items().get(0).id();
        GoodsReceiptDto gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), 1L, "CH-" + po.id(),
                "MH01GS0001", List.of(new CreateGrRequest.GrLineRequest(poItemId, bd("100"), 1L,
                        "HEAT-" + po.id(), "CERT-" + po.id(), null, true))));
        login(clerk, "ACCOUNTANT");
        return new Receipt(gr.id(), supplierId);
    }

    private Long account(String code) {
        return jdbc.queryForObject("SELECT id FROM ims.gl_accounts WHERE account_code = ?", Long.class, code);
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

    private Long newUser(String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Tax Test', ?, 'x') RETURNING id
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

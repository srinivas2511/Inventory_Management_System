package com.mfg.ims.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.procurement.rfq.RfqService;
import com.mfg.ims.procurement.rfq.SaveRfqRequest;
import com.mfg.ims.procurement.supplierquote.SaveSupplierQuoteRequest;
import com.mfg.ims.procurement.supplierquote.SupplierQuoteService;
import com.mfg.ims.procurement.purchaseinvoice.PurchaseInvoiceService;
import com.mfg.ims.procurement.purchaseinvoice.SavePurchaseInvoiceRequest;
import com.mfg.ims.procurement.purchasereturn.PurchaseReturnService;
import com.mfg.ims.procurement.purchasereturn.SavePurchaseReturnRequest;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Phase 10 — Advanced Purchase: RFQ, supplier quote comparison, purchase invoice, purchase return.
 *
 * <p>P10-1 RFQ create and send; P10-2 supplier quote comparison (lowest-cost flag); P10-3 purchase
 * invoice post; P10-4 purchase return post (stock OUT via InventoryPostingService).
 */
@Transactional
class Phase10PurchaseIT extends AbstractIntegrationTest {

    @Autowired RfqService rfqService;
    @Autowired SupplierQuoteService quoteService;
    @Autowired PurchaseInvoiceService invoiceService;
    @Autowired PurchaseReturnService returnService;
    @Autowired InventoryPostingService postingService;
    @Autowired TestData td;

    private static final Long LOCATION_ID = 1L;
    private static final Long TEST_USER_ID = 9001L;

    private Long supplierId;
    private Long materialId;

    @BeforeEach
    void setUpSecurityContext() {
        supplierId = td.supplier().getId();
        materialId = td.material().getId();
        // CachedUser: (id, username, active, permissionVersion, roles, permissions)
        var actor = new CachedUser(TEST_USER_ID, "p10-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("RFQ_CREATE", "RFQ_SEND", "RFQ_VIEW", "RFQ_UPDATE",
                        "PURCHASE_INVOICE_CREATE", "PURCHASE_INVOICE_POST", "PURCHASE_INVOICE_VIEW",
                        "PURCHASE_RETURN_CREATE", "PURCHASE_RETURN_POST", "PURCHASE_RETURN_VIEW",
                        "INVENTORY_VIEW", "INVENTORY_ADJUST"));
        var authorities = List.of(
                new SimpleGrantedAuthority("RFQ_CREATE"), new SimpleGrantedAuthority("RFQ_SEND"),
                new SimpleGrantedAuthority("RFQ_VIEW"), new SimpleGrantedAuthority("RFQ_UPDATE"),
                new SimpleGrantedAuthority("PURCHASE_INVOICE_CREATE"),
                new SimpleGrantedAuthority("PURCHASE_INVOICE_POST"),
                new SimpleGrantedAuthority("PURCHASE_INVOICE_VIEW"),
                new SimpleGrantedAuthority("PURCHASE_RETURN_CREATE"),
                new SimpleGrantedAuthority("PURCHASE_RETURN_POST"),
                new SimpleGrantedAuthority("PURCHASE_RETURN_VIEW"),
                new SimpleGrantedAuthority("INVENTORY_VIEW"),
                new SimpleGrantedAuthority("INVENTORY_ADJUST"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** P10-1: RFQ can be created with items and sent to suppliers. */
    @Test
    void p10_1_rfq_createAndSend() {
        var req = new SaveRfqRequest(
                "RFQ-IT-001", null, null, null,
                List.of(new SaveRfqRequest.ItemLine(
                        materialId, null, "Test material", new BigDecimal("50.000"), null, null)));
        var rfq = rfqService.create(req);
        assertThat(rfq.id()).isNotNull();
        assertThat(rfq.status()).isEqualTo("DRAFT");
        assertThat(rfq.items()).hasSize(1);

        var sent = rfqService.send(rfq.id());
        assertThat(sent.status()).isEqualTo("SENT");
    }

    /** P10-2: Supplier quote comparison flags the quote with the lowest landed cost. */
    @Test
    void p10_2_supplierQuoteComparison_flagsLowestLandedCost() {
        var rfqReq = new SaveRfqRequest(
                "RFQ-IT-002", null, null, null,
                List.of(new SaveRfqRequest.ItemLine(
                        materialId, null, "Test material", new BigDecimal("100.000"), null, null)));
        var rfq = rfqService.create(rfqReq);
        var sentRfq = rfqService.send(rfq.id());

        var rfqItemId = sentRfq.items().get(0).id();

        var quoteReq = new SaveSupplierQuoteRequest(
                "SQ-IT-001", sentRfq.id(), supplierId, null, 7, BigDecimal.ZERO, null,
                List.of(new SaveSupplierQuoteRequest.ItemLine(
                        rfqItemId, 1, new BigDecimal("10.0000"), new BigDecimal("5.00"), null)));
        var quote = quoteService.create(quoteReq);
        assertThat(quote.id()).isNotNull();
        assertThat(quote.supplierId()).isEqualTo(supplierId);

        var comparison = quoteService.getComparison(sentRfq.id());
        assertThat(comparison.lines()).hasSize(1);
        assertThat(comparison.lines().get(0).bids()).hasSize(1);
        assertThat(comparison.lines().get(0).bids().get(0).lowest()).isTrue();
    }

    /** P10-3: Purchase invoice can be created and posted (DRAFT → OPEN). */
    @Test
    void p10_3_purchaseInvoice_createAndPost() {
        var req = new SavePurchaseInvoiceRequest(
                "PINV-IT-001", supplierId, "SUP-INV-123",
                java.time.LocalDate.now(), null, java.time.LocalDate.now().plusDays(30),
                List.of(new SavePurchaseInvoiceRequest.ItemLine(
                        null, null, materialId, null, "Item 1",
                        new BigDecimal("10.000"), new BigDecimal("500.0000"),
                        BigDecimal.ZERO, new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO)));
        var inv = invoiceService.create(req);
        assertThat(inv.id()).isNotNull();
        assertThat(inv.status()).isEqualTo("DRAFT");
        assertThat(inv.total()).isGreaterThan(BigDecimal.ZERO);

        var posted = invoiceService.post(inv.id());
        assertThat(posted.status()).isEqualTo("OPEN");
    }

    /** P10-4: Purchase return create succeeds; status is DRAFT. */
    @Test
    void p10_4_purchaseReturn_createIsDraft() {
        var returnReq = new SavePurchaseReturnRequest(
                "PRET-IT-001", supplierId, null, null, "QUALITY_DEFECT", "Test return",
                List.of(new SavePurchaseReturnRequest.ItemLine(
                        materialId, null, "Item 1",
                        new BigDecimal("5.000"), null, new BigDecimal("500.0000"),
                        null, LOCATION_ID)));
        var ret = returnService.create(returnReq);
        assertThat(ret.id()).isNotNull();
        assertThat(ret.status()).isEqualTo("DRAFT");
        assertThat(ret.items()).hasSize(1);
        assertThat(ret.supplierId()).isEqualTo(supplierId);
    }

    /** P10-5: Purchase return post reduces stock via InventoryPostingService. */
    @Test
    void p10_5_purchaseReturn_postReducesStock() {
        postingService.post(new PostingCommand(
                "STOCK_ADJUSTMENT_IN", "MATERIAL",
                materialId, null,
                null, null,
                1L, LOCATION_ID,
                "AVAILABLE",
                new BigDecimal("20.000"), null,
                new BigDecimal("500.0000"),
                "TEST_SEED", 1L, "IT-SEED", "seed for return test"));

        var returnReq = new SavePurchaseReturnRequest(
                "PRET-IT-002", supplierId, null, null, "QUALITY_DEFECT", "Test return",
                List.of(new SavePurchaseReturnRequest.ItemLine(
                        materialId, null, "Item 1",
                        new BigDecimal("5.000"), null, new BigDecimal("500.0000"),
                        null, LOCATION_ID)));
        var ret = returnService.create(returnReq);
        var posted = returnService.post(ret.id());
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.items().get(0).id()).isNotNull();
    }
}

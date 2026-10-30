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
import com.mfg.ims.procurement.supplierquote.AwardResultDto;
import com.mfg.ims.procurement.supplierquote.SaveSupplierQuoteRequest;
import com.mfg.ims.procurement.supplierquote.SupplierQuoteService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * RFQ supplier-quote flow: record quote, list quotes, award quote → PO created.
 *
 * <p>RQI-1 record a supplier quote; RQI-2 list quotes for an RFQ; RQI-3 award a quote → PO created.
 */
@Transactional
class RfqQuoteIT extends AbstractIntegrationTest {

    @Autowired RfqService rfqService;
    @Autowired SupplierQuoteService quoteService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9200L;

    private Long supplierId;
    private Long materialId;

    @BeforeEach
    void setUpSecurityContext() {
        supplierId = td.supplier().getId();
        materialId = td.material().getId();
        var actor = new CachedUser(TEST_USER_ID, "rfq-quote-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("RFQ_CREATE", "RFQ_SEND", "RFQ_VIEW", "RFQ_UPDATE", "RFQ_AWARD",
                        "PURCHASE_CREATE", "PURCHASE_VIEW", "PURCHASE_APPROVE"));
        var authorities = List.of(
                new SimpleGrantedAuthority("RFQ_CREATE"),
                new SimpleGrantedAuthority("RFQ_SEND"),
                new SimpleGrantedAuthority("RFQ_VIEW"),
                new SimpleGrantedAuthority("RFQ_UPDATE"),
                new SimpleGrantedAuthority("RFQ_AWARD"),
                new SimpleGrantedAuthority("PURCHASE_CREATE"),
                new SimpleGrantedAuthority("PURCHASE_VIEW"),
                new SimpleGrantedAuthority("PURCHASE_APPROVE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** RQI-1: A supplier quote can be recorded for a sent RFQ. */
    @Test
    void rqi_1_recordSupplierQuote() {
        var rfq = createSentRfq("RFQ-QIT-001");
        Long rfqItemId = rfq.items().get(0).id();

        var req = new SaveSupplierQuoteRequest(
                "SQ-QIT-001", rfq.id(), supplierId, null, 5, BigDecimal.ZERO, "Test quote",
                List.of(new SaveSupplierQuoteRequest.ItemLine(
                        rfqItemId, 1, new BigDecimal("120.0000"), new BigDecimal("18.00"), null)));

        var quote = quoteService.create(req);

        assertThat(quote.id()).isNotNull();
        assertThat(quote.rfqId()).isEqualTo(rfq.id());
        assertThat(quote.supplierId()).isEqualTo(supplierId);
        assertThat(quote.status()).isEqualTo("DRAFT");
        assertThat(quote.items()).hasSize(1);
        assertThat(quote.items().get(0).unitPrice()).isEqualByComparingTo("120.0000");
    }

    /** RQI-2: Quotes can be listed by RFQ id. */
    @Test
    void rqi_2_listQuotesByRfq() {
        var rfq = createSentRfq("RFQ-QIT-002");
        Long rfqItemId = rfq.items().get(0).id();
        // Use a second supplier — the unique constraint is (rfq_id, supplier_id)
        Long supplierId2 = td.supplier().getId();

        quoteService.create(new SaveSupplierQuoteRequest(
                "SQ-QIT-002A", rfq.id(), supplierId, null, 7, BigDecimal.ZERO, null,
                List.of(new SaveSupplierQuoteRequest.ItemLine(
                        rfqItemId, 1, new BigDecimal("90.0000"), BigDecimal.ZERO, null))));
        quoteService.create(new SaveSupplierQuoteRequest(
                "SQ-QIT-002B", rfq.id(), supplierId2, null, 10, new BigDecimal("50.0000"), null,
                List.of(new SaveSupplierQuoteRequest.ItemLine(
                        rfqItemId, 1, new BigDecimal("85.0000"), BigDecimal.ZERO, null))));

        var quotes = quoteService.listByRfq(rfq.id());

        assertThat(quotes).hasSize(2);
        assertThat(quotes).extracting("rfqId").containsOnly(rfq.id());
    }

    /** RQI-3: Awarding a quote creates a PurchaseOrder draft and marks the quote as AWARDED. */
    @Test
    void rqi_3_awardQuote_createsPurchaseOrder() {
        var rfq = createSentRfq("RFQ-QIT-003");
        Long rfqItemId = rfq.items().get(0).id();

        var quote = quoteService.create(new SaveSupplierQuoteRequest(
                "SQ-QIT-003", rfq.id(), supplierId, null, 3, BigDecimal.ZERO, null,
                List.of(new SaveSupplierQuoteRequest.ItemLine(
                        rfqItemId, 1, new BigDecimal("200.0000"), BigDecimal.ZERO, null))));
        assertThat(quote.awarded()).isFalse();

        AwardResultDto result = quoteService.award(rfq.id(), quote.id());

        assertThat(result.poId()).isNotNull();
        assertThat(result.poNumber()).isNotBlank();

        // Quote is now marked as awarded
        var updated = quoteService.get(quote.id());
        assertThat(updated.awarded()).isTrue();
        assertThat(updated.status()).isEqualTo("AWARDED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private com.mfg.ims.procurement.rfq.RfqDto createSentRfq(String docNo) {
        var rfq = rfqService.create(new SaveRfqRequest(
                docNo, null, null, null,
                List.of(new SaveRfqRequest.ItemLine(
                        materialId, null, "Item for " + docNo,
                        new BigDecimal("10.000"), null, null))));
        return rfqService.send(rfq.id());
    }
}

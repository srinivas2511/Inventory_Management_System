package com.mfg.ims.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.sales.orderconfirmation.OcService;
import com.mfg.ims.sales.orderconfirmation.SaveOcRequest;
import com.mfg.ims.sales.quotation.QuotationService;
import com.mfg.ims.sales.quotation.SaveQuotationRequest;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * QuotationCancelIT — cancel cascade guard (Task 11.8)
 *
 * QC-CANCEL-1: A quotation with no linked OC can be cancelled successfully.
 * QC-CANCEL-2: A quotation linked to a non-cancelled OC cannot be cancelled (409 CONFLICT).
 */
@Transactional
class QuotationCancelIT extends AbstractIntegrationTest {

    @Autowired QuotationService quotationService;
    @Autowired OcService ocService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9200L;
    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = td.customer().getId();
        var actor = new CachedUser(TEST_USER_ID, "qcancel-test", true, 1,
                Set.of("SALES_MANAGER"),
                Set.of("QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE",
                        "QUOTATION_CANCEL", "QUOTATION_CONVERT", "QUOTATION_AMEND",
                        "OC_VIEW", "OC_CREATE", "OC_UPDATE", "OC_CONFIRM", "OC_CANCEL",
                        "OC_SUBMIT", "OC_APPROVE", "OC_CREATE_WORKORDER"));
        var authorities = actor.permissions().stream()
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** QC-CANCEL-1: A quotation with no linked OC should cancel successfully. */
    @Test
    void qc_cancel_1_unconverted_quotation_can_be_cancelled() {
        var req = new SaveQuotationRequest(
                customerId, null, null, "INR", null, "ENQ-CANCEL-1", null,
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Widget X",
                        new BigDecimal("5"), "PCS", new BigDecimal("100.00"),
                        null, null, null)));
        var qt = quotationService.create(req);
        quotationService.submit(qt.id());

        var cancelled = quotationService.cancel(qt.id(), "No longer needed");

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    /** QC-CANCEL-2: A quotation linked to an active OC must fail cancel with 409. */
    @Test
    void qc_cancel_2_converted_quotation_cannot_be_cancelled() {
        var req = new SaveQuotationRequest(
                customerId, null, null, "INR", null, "ENQ-CANCEL-2", null,
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Widget Y",
                        new BigDecimal("10"), "PCS", new BigDecimal("200.00"),
                        null, null, null)));
        var qt = quotationService.create(req);
        quotationService.submit(qt.id());

        // convert quotation to OC (marks quotation as CONVERTED and creates an OC)
        ocService.convertFromQuotation(qt.id());

        // quotation is now CONVERTED; trying to cancel should throw CONFLICT
        assertThatThrownBy(() -> quotationService.cancel(qt.id(), "Try to cancel"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONVERTED");
    }
}

package com.mfg.ims.sales;

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
import com.mfg.ims.sales.challan.ChallanService;
import com.mfg.ims.sales.challan.SaveChallanRequest;
import com.mfg.ims.sales.orderconfirmation.OcService;
import com.mfg.ims.sales.orderconfirmation.SaveOcRequest;
import com.mfg.ims.sales.quotation.QuotationService;
import com.mfg.ims.sales.quotation.SaveQuotationRequest;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Phase 11 — Quotations, Order Confirmations, Delivery Challans.
 *
 * <p>P11-1 Quotation create+submit; P11-2 Quotation cancel; P11-3 OC create from quotation;
 * P11-4 OC confirm; P11-5 Delivery challan create+issue.
 */
@Transactional
class Phase11SalesIT extends AbstractIntegrationTest {

    @Autowired QuotationService quotationService;
    @Autowired OcService ocService;
    @Autowired ChallanService challanService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9011L;

    private Long customerId;

    @BeforeEach
    void setUpSecurityContext() {
        customerId = td.customer().getId();
        var actor = new CachedUser(TEST_USER_ID, "p11-test-user", true, 1,
                Set.of("SALES_MANAGER"),
                Set.of("QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE",
                        "QUOTATION_CANCEL", "QUOTATION_CONVERT",
                        "OC_VIEW", "OC_CREATE", "OC_UPDATE", "OC_CONFIRM", "OC_CANCEL",
                        "CHALLAN_VIEW", "CHALLAN_CREATE", "CHALLAN_ISSUE", "CHALLAN_RETURN"));
        var authorities = List.of(
                new SimpleGrantedAuthority("QUOTATION_VIEW"),
                new SimpleGrantedAuthority("QUOTATION_CREATE"),
                new SimpleGrantedAuthority("QUOTATION_UPDATE"),
                new SimpleGrantedAuthority("QUOTATION_CANCEL"),
                new SimpleGrantedAuthority("QUOTATION_CONVERT"),
                new SimpleGrantedAuthority("OC_VIEW"),
                new SimpleGrantedAuthority("OC_CREATE"),
                new SimpleGrantedAuthority("OC_UPDATE"),
                new SimpleGrantedAuthority("OC_CONFIRM"),
                new SimpleGrantedAuthority("OC_CANCEL"),
                new SimpleGrantedAuthority("CHALLAN_VIEW"),
                new SimpleGrantedAuthority("CHALLAN_CREATE"),
                new SimpleGrantedAuthority("CHALLAN_ISSUE"),
                new SimpleGrantedAuthority("CHALLAN_RETURN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void p11_1_quotation_create_and_submit() {
        var req = new SaveQuotationRequest(
                customerId, null, null, "INR", null, "ENQ-001", "Test quotation",
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Test product",
                        BigDecimal.TEN, "PCS", new BigDecimal("500.00"), null, 7, null)));

        var created = quotationService.create(req);
        assertThat(created.id()).isNotNull();
        assertThat(created.docNo()).startsWith("QT/");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.items()).hasSize(1);

        var submitted = quotationService.submit(created.id());
        assertThat(submitted.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void p11_2_quotation_cancel() {
        var req = new SaveQuotationRequest(
                customerId, null, null, "INR", null, null, null,
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Item",
                        BigDecimal.ONE, "PCS", new BigDecimal("100.00"), null, null, null)));

        var created = quotationService.create(req);
        var cancelled = quotationService.cancel(created.id(), "Customer withdrew");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void p11_3_oc_create_from_quotation() {
        var qtReq = new SaveQuotationRequest(
                customerId, null, null, "INR", null, "ENQ-002", null,
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Product",
                        BigDecimal.TEN, "PCS", new BigDecimal("200.00"), null, null, null)));
        var qt = quotationService.create(qtReq);
        quotationService.submit(qt.id());

        var ocReq = new SaveOcRequest(
                customerId, null, "DOMESTIC", qt.id(), "CUST-PO-001", null, null,
                "INR", null, null,
                List.of(new SaveOcRequest.ItemLine(
                        null, td.product().getId(), "Product",
                        BigDecimal.TEN, "PCS", new BigDecimal("200.00"), null, null)));

        var oc = ocService.create(ocReq);
        assertThat(oc.id()).isNotNull();
        assertThat(oc.docNo()).startsWith("OC/");
        assertThat(oc.status()).isEqualTo("DRAFT");
        assertThat(oc.quotationId()).isEqualTo(qt.id());
        assertThat(oc.customerPoNo()).isEqualTo("CUST-PO-001");
        assertThat(oc.items()).hasSize(1);

        var updatedQt = quotationService.get(qt.id());
        assertThat(updatedQt.status()).isEqualTo("CONVERTED");
    }

    @Test
    void p11_4_oc_confirm() {
        var ocReq = new SaveOcRequest(
                customerId, null, "DOMESTIC", null, "PO-002", null, null,
                "INR", null, null,
                List.of(new SaveOcRequest.ItemLine(
                        null, td.product().getId(), "Prod",
                        new BigDecimal("5"), "PCS", new BigDecimal("300.00"), null, null)));

        var oc = ocService.create(ocReq);
        var confirmed = ocService.confirm(oc.id());
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void p11_5_delivery_challan_create_and_issue() {
        var challanReq = new SaveChallanRequest(
                customerId, null, "DELIVERY", null, null,
                "123 Main St, City", "MH01AB1234", "John Driver",
                "LR-001", null, "Handle with care",
                List.of(new SaveChallanRequest.ItemLine(
                        null, td.product().getId(), "Product",
                        new BigDecimal("5"), "PCS", new BigDecimal("200.00"), null, null)));

        var challan = challanService.create(challanReq);
        assertThat(challan.id()).isNotNull();
        assertThat(challan.docNo()).startsWith("DC/");
        assertThat(challan.status()).isEqualTo("DRAFT");
        assertThat(challan.lrNo()).isEqualTo("LR-001");
        assertThat(challan.items()).hasSize(1);

        var issued = challanService.issue(challan.id());
        assertThat(issued.status()).isEqualTo("ISSUED");
    }
}

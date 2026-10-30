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
import com.mfg.ims.sales.challan.ChallanService;
import com.mfg.ims.sales.challan.SaveChallanRequest;
import com.mfg.ims.sales.orderconfirmation.OcService;
import com.mfg.ims.sales.orderconfirmation.SaveOcRequest;
import com.mfg.ims.sales.quotation.QuotationService;
import com.mfg.ims.sales.quotation.SaveQuotationRequest;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * QOC-1 Convert approved quotation to OC via convertFromQuotation.
 * QOC-2 Create work order from an APPROVED OC.
 * QOC-3 Delivery challan create and issue.
 */
@Transactional
class QuotationOcIT extends AbstractIntegrationTest {

    @Autowired QuotationService quotationService;
    @Autowired OcService ocService;
    @Autowired ChallanService challanService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9100L;

    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = td.customer().getId();
        var actor = new CachedUser(TEST_USER_ID, "qoc-test", true, 1,
                Set.of("SALES_MANAGER"),
                Set.of("QUOTATION_VIEW", "QUOTATION_CREATE", "QUOTATION_UPDATE",
                        "QUOTATION_CANCEL", "QUOTATION_CONVERT",
                        "OC_VIEW", "OC_CREATE", "OC_UPDATE", "OC_CONFIRM", "OC_CANCEL",
                        "OC_SUBMIT", "OC_APPROVE", "OC_CREATE_WORKORDER",
                        "CHALLAN_VIEW", "CHALLAN_CREATE", "CHALLAN_ISSUE", "CHALLAN_RETURN"));
        var authorities = actor.permissions().stream()
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** QOC-1: Submit a quotation then convert it to an Order Confirmation. */
    @Test
    void qoc_1_convert_submitted_quotation_to_oc() {
        var qtReq = new SaveQuotationRequest(
                customerId, null, null, "INR", null, "ENQ-QOC-1", null,
                List.of(new SaveQuotationRequest.ItemLine(
                        null, td.product().getId(), "Spring widget",
                        new BigDecimal("20"), "PCS", new BigDecimal("150.00"),
                        null, null, null)));
        var qt = quotationService.create(qtReq);
        quotationService.submit(qt.id());

        var result = ocService.convertFromQuotation(qt.id());

        assertThat(result.ocId()).isNotNull();
        assertThat(result.ocNumber()).startsWith("OC/");

        var updatedQt = quotationService.get(qt.id());
        assertThat(updatedQt.status()).isEqualTo("CONVERTED");

        var oc = ocService.get(result.ocId());
        assertThat(oc.quotationId()).isEqualTo(qt.id());
        assertThat(oc.customerId()).isEqualTo(customerId);
        assertThat(oc.items()).hasSize(1);
    }

    /** QOC-2: Confirm an OC then create a production work order from it. */
    @Test
    void qoc_2_create_work_order_from_oc() {
        var product = td.product();
        td.draftBom(product.getId()); // ensure a BOM exists for the product

        var ocReq = new SaveOcRequest(
                customerId, null, "DOMESTIC", null, "WO-PO-001", null, null,
                "INR", null, "WO test",
                List.of(new SaveOcRequest.ItemLine(
                        null, product.getId(), "Widget A",
                        new BigDecimal("10"), "PCS", new BigDecimal("200.00"), null, null)));
        var oc = ocService.create(ocReq);
        // confirm (DRAFT → CONFIRMED) — existing method; CONFIRMED is accepted by createWorkOrder
        ocService.confirm(oc.id());

        var woResult = ocService.createWorkOrder(oc.id());

        assertThat(woResult.workOrderId()).isNotNull();
        assertThat(woResult.workOrderNumber()).isNotBlank();
    }

    /** QOC-3: Create a delivery challan and issue it. */
    @Test
    void qoc_3_delivery_challan_create_and_issue() {
        var challanReq = new SaveChallanRequest(
                customerId, null, "NON_RETURNABLE", null, null,
                "456 Industrial Park, City", "GJ01CD5678", "Raju Driver",
                "LR-QOC-001", null, "Fragile items",
                List.of(new SaveChallanRequest.ItemLine(
                        null, td.product().getId(), "Widget B",
                        new BigDecimal("3"), "PCS", new BigDecimal("500.00"), null, null)));

        var challan = challanService.create(challanReq);
        assertThat(challan.id()).isNotNull();
        assertThat(challan.docNo()).startsWith("DC/");
        assertThat(challan.status()).isEqualTo("DRAFT");
        assertThat(challan.challanKind()).isEqualTo("NON_RETURNABLE");
        assertThat(challan.items()).hasSize(1);

        var issued = challanService.issue(challan.id());
        assertThat(issued.status()).isEqualTo("ISSUED");
    }
}

package com.mfg.ims.finance;

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
import com.mfg.ims.finance.invoice.SaveInvoiceRequest;
import com.mfg.ims.finance.invoice.SalesInvoiceService;
import com.mfg.ims.finance.payment.PaymentService;
import com.mfg.ims.finance.payment.SavePaymentRequest;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Phase 12 — Sales invoices, payments, open items.
 *
 * <p>P12-1 Create sales invoice; P12-2 Post sales invoice; P12-3 Cancel sales invoice;
 * P12-4 Create payment (receipt); P12-5 Post payment.
 */
@Transactional
class Phase12FinanceIT extends AbstractIntegrationTest {

    @Autowired SalesInvoiceService invoiceService;
    @Autowired PaymentService paymentService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9012L;

    private Long customerId;

    @BeforeEach
    void setUpSecurityContext() {
        customerId = td.customer().getId();
        var actor = new CachedUser(TEST_USER_ID, "p12-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("SALES_INVOICE_VIEW", "SALES_INVOICE_CREATE", "SALES_INVOICE_POST",
                        "SALES_INVOICE_CANCEL", "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_POST",
                        "OPEN_ITEM_VIEW"));
        var authorities = List.of(
                new SimpleGrantedAuthority("SALES_INVOICE_VIEW"),
                new SimpleGrantedAuthority("SALES_INVOICE_CREATE"),
                new SimpleGrantedAuthority("SALES_INVOICE_POST"),
                new SimpleGrantedAuthority("SALES_INVOICE_CANCEL"),
                new SimpleGrantedAuthority("PAYMENT_VIEW"),
                new SimpleGrantedAuthority("PAYMENT_CREATE"),
                new SimpleGrantedAuthority("PAYMENT_POST"),
                new SimpleGrantedAuthority("OPEN_ITEM_VIEW"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, authorities));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void p12_1_create_sales_invoice() {
        var req = new SaveInvoiceRequest(customerId, null, "TAX", "B2B", "27",
                null, null, null, "CUST-PO-001", null, "INR", "Test invoice", null, null,
                List.of(new SaveInvoiceRequest.ItemLine(
                        td.product().getId(), null, "Product A",
                        new BigDecimal("10"), "PCS", new BigDecimal("1000.00"),
                        BigDecimal.ZERO, "8482")));

        var created = invoiceService.create(req, TEST_USER_ID);
        assertThat(created.id()).isNotNull();
        assertThat(created.docNo()).startsWith("SI/");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.customerId()).isEqualTo(customerId);
        assertThat(created.grandTotal()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(created.items()).hasSize(1);
    }

    @Test
    void p12_2_post_sales_invoice() {
        var req = new SaveInvoiceRequest(customerId, null, "TAX", "B2B", null,
                null, null, null, null, null, "INR", null, null, null,
                List.of(new SaveInvoiceRequest.ItemLine(
                        td.product().getId(), null, "Product B",
                        new BigDecimal("5"), "PCS", new BigDecimal("2000.00"),
                        null, null)));

        var created = invoiceService.create(req, TEST_USER_ID);
        var posted = invoiceService.post(created.id(), TEST_USER_ID);
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.postedAt()).isNotNull();
    }

    @Test
    void p12_3_cancel_sales_invoice() {
        var req = new SaveInvoiceRequest(customerId, null, "TAX", "B2B", null,
                null, null, null, null, null, "INR", null, null, null,
                List.of(new SaveInvoiceRequest.ItemLine(
                        td.product().getId(), null, "Product C",
                        BigDecimal.ONE, "PCS", new BigDecimal("500.00"),
                        null, null)));

        var created = invoiceService.create(req, TEST_USER_ID);
        var cancelled = invoiceService.cancel(created.id(), "Customer cancelled order", TEST_USER_ID);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledReason()).isEqualTo("Customer cancelled order");
    }

    @Test
    void p12_4_create_payment_receipt() {
        var req = new SavePaymentRequest(
                "RECEIPT", "REGULAR", "CUSTOMER", customerId,
                "NEFT", "REF-001", null, "INR",
                new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO, "Test receipt");

        var created = paymentService.create(req, TEST_USER_ID);
        assertThat(created.id()).isNotNull();
        assertThat(created.docNo()).startsWith("PAY/");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.direction()).isEqualTo("RECEIPT");
        assertThat(created.amount()).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void p12_5_post_payment() {
        var req = new SavePaymentRequest(
                "PAYMENT", "REGULAR", "SUPPLIER", 1L,
                "RTGS", "REF-002", null, "INR",
                new BigDecimal("12000.00"), BigDecimal.ZERO, BigDecimal.ZERO, null);

        var created = paymentService.create(req, TEST_USER_ID);
        var posted = paymentService.post(created.id(), TEST_USER_ID);
        assertThat(posted.status()).isEqualTo("POSTED");
    }
}

package com.mfg.ims.finance;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.finance.invoice.SalesInvoice;
import com.mfg.ims.finance.invoice.SaveInvoiceRequest;
import com.mfg.ims.finance.invoice.SalesInvoiceService;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.service.SalesOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ERP-D-10 — Direct invoice auto-dispatch and cancel dependency.
 *
 * <p>ID-1 Auto-dispatch created when posting a sales-order-linked invoice with no dispatch;
 * ID-2 Post without sales order — no dispatch auto-created;
 * ID-3 Cancel blocked when balance < grand_total (partial payment applied).
 */
@Transactional
class InvoiceDispatchIT extends AbstractIntegrationTest {

    @Autowired SalesInvoiceService invoiceService;
    @Autowired SalesOrderService soService;
    @Autowired TestData td;
    @Autowired EntityManager em;

    private static final Long TEST_USER_ID = 9020L;
    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = td.customer().getId();
        var actor = new CachedUser(TEST_USER_ID, "id-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("SALES_INVOICE_VIEW", "SALES_INVOICE_CREATE", "SALES_INVOICE_POST",
                        "SALES_INVOICE_CANCEL", "SALES_VIEW", "SALES_MANAGE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(
                        new SimpleGrantedAuthority("SALES_INVOICE_VIEW"),
                        new SimpleGrantedAuthority("SALES_INVOICE_CREATE"),
                        new SimpleGrantedAuthority("SALES_INVOICE_POST"),
                        new SimpleGrantedAuthority("SALES_INVOICE_CANCEL"),
                        new SimpleGrantedAuthority("SALES_VIEW"),
                        new SimpleGrantedAuthority("SALES_MANAGE"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void id_1_auto_dispatch_created_on_post_when_so_linked() {
        // Create a sales order
        var so = soService.create(new CreateSalesOrderRequest(
                customerId, LocalDate.now(), null, "123 Main St, City", null, null, null));

        // Create invoice linked to SO, no dispatchId
        var req = new SaveInvoiceRequest(customerId, LocalDate.now(), "TAX", "B2B", null,
                null, so.id(), null, null, null, "INR", null, null, null, List.of(
                        new SaveInvoiceRequest.ItemLine(td.product().getId(), null, "Item A",
                                BigDecimal.ONE, "PCS", new BigDecimal("1000.00"), null, null)));
        var inv = invoiceService.create(req, TEST_USER_ID);
        assertThat(inv.dispatchId()).isNull();

        // Post — should auto-create dispatch
        var posted = invoiceService.post(inv.id(), TEST_USER_ID);
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.dispatchId()).isNotNull();
    }

    @Test
    void id_2_no_auto_dispatch_when_no_so() {
        // Create invoice with no salesOrderId
        var req = new SaveInvoiceRequest(customerId, LocalDate.now(), "TAX", "B2B", null,
                null, null, null, null, null, "INR", null, null, null, List.of(
                        new SaveInvoiceRequest.ItemLine(td.product().getId(), null, "Item B",
                                BigDecimal.ONE, "PCS", new BigDecimal("500.00"), null, null)));
        var inv = invoiceService.create(req, TEST_USER_ID);

        var posted = invoiceService.post(inv.id(), TEST_USER_ID);
        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.dispatchId()).isNull();
    }

    @Test
    void id_3_cancel_blocked_when_payment_applied() {
        // Create and post an invoice
        var req = new SaveInvoiceRequest(customerId, LocalDate.now(), "TAX", "B2B", null,
                null, null, null, null, null, "INR", null, null, null, List.of(
                        new SaveInvoiceRequest.ItemLine(td.product().getId(), null, "Item C",
                                BigDecimal.ONE, "PCS", new BigDecimal("2000.00"), null, null)));
        var inv = invoiceService.create(req, TEST_USER_ID);
        var posted = invoiceService.post(inv.id(), TEST_USER_ID);

        // Simulate partial payment via JPA — use the entity directly so Hibernate's dirty-tracking
        // picks up the change; flush to DB then evict so cancel() reads the updated value.
        var entity = em.find(SalesInvoice.class, posted.id());
        entity.setBalanceInr(new java.math.BigDecimal("500.0000"));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> invoiceService.cancel(posted.id(), "test", TEST_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot cancel invoice with applied payments");
    }
}

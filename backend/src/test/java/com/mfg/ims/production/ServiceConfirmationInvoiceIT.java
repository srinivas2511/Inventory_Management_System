package com.mfg.ims.production;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.production.serviceorder.ConfirmServiceRequest;
import com.mfg.ims.production.serviceorder.CreateGrnFromConfRequest;
import com.mfg.ims.production.serviceorder.CreateServiceOrderRequest;
import com.mfg.ims.production.serviceorder.ServiceInvoiceRequest;
import com.mfg.ims.production.serviceorder.ServiceOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 14.2 Service confirmation: inward (GRN) and invoice.
 *
 * <p>SC-INW-1 Create inward (GRN) from confirmation; SC-INV-1 Create sales invoice from confirmation.
 */
@Transactional
class ServiceConfirmationInvoiceIT extends AbstractIntegrationTest {

    @Autowired ServiceOrderService service;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;

    private static final Long TEST_USER_ID = 9_213L;

    @BeforeEach
    void setUp() {
        var actor = new CachedUser(TEST_USER_ID, "sc-inv-user", true, 1,
                Set.of("ADMIN"),
                Set.of("SERVICE_ORDER_VIEW", "SERVICE_ORDER_CREATE",
                        "INVENTORY_RECEIVE", "SALES_INVOICE_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(
                        new SimpleGrantedAuthority("SERVICE_ORDER_VIEW"),
                        new SimpleGrantedAuthority("SERVICE_ORDER_CREATE"),
                        new SimpleGrantedAuthority("INVENTORY_RECEIVE"),
                        new SimpleGrantedAuthority("SALES_INVOICE_CREATE"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sc_inw_1_create_inward_from_confirmation() {
        // Arrange: create a service order and confirm it
        var vendor = td.supplier();
        var material = td.material();
        var soReq = new CreateServiceOrderRequest(
                LocalDate.now(), "AMC", vendor.getId(), null,
                "Test inward service", LocalDate.now(), new BigDecimal("1000.00"),
                new BigDecimal("180.00"), "Test");
        var so = service.create(soReq, TEST_USER_ID);

        var conf = service.confirm(so.id(), new ConfirmServiceRequest("Confirmed"), TEST_USER_ID);

        // Look up the seeded main warehouse and a raw-material location
        Long warehouseId = jdbc.queryForObject(
                "SELECT id FROM ims.warehouses WHERE warehouse_code = 'MAIN-WH'", Long.class);
        Long locationId = jdbc.queryForObject(
                "SELECT id FROM ims.warehouse_locations WHERE warehouse_id = ? LIMIT 1", Long.class, warehouseId);

        var inwardReq = new CreateGrnFromConfRequest(
                warehouseId, locationId, LocalDate.now(),
                List.of(new CreateGrnFromConfRequest.ItemLine(
                        material.getId(), new BigDecimal("5.000"), new BigDecimal("200.00"))));

        // Act
        var grDto = service.createInwardFromConfirmation(conf.id(), inwardReq, TEST_USER_ID);

        // Assert
        assertThat(grDto.id()).isNotNull();
        assertThat(grDto.grNumber()).startsWith("GR");
        assertThat(grDto.serviceConfirmationId()).isEqualTo(conf.id());
        assertThat(grDto.status()).isEqualTo("POSTED");
        assertThat(grDto.items()).hasSize(1);
        assertThat(grDto.items().get(0).materialId()).isEqualTo(material.getId());
        assertThat(grDto.items().get(0).receivedQty()).isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void sc_inv_1_create_invoice_from_confirmation() {
        // Arrange: create a service order with a customer and confirm it
        var customer = td.customer();
        var soReq = new CreateServiceOrderRequest(
                LocalDate.now(), "INSTALLATION", null, customer.getId(),
                "Test invoice service", LocalDate.now(), new BigDecimal("5000.00"),
                new BigDecimal("900.00"), null);
        var so = service.create(soReq, TEST_USER_ID);
        var conf = service.confirm(so.id(), null, TEST_USER_ID);

        var invoiceReq = new ServiceInvoiceRequest(
                LocalDate.now(), customer.getId(),
                List.of(new ServiceInvoiceRequest.LineItem(
                        "Installation Labour", new BigDecimal("10.000"),
                        new BigDecimal("500.00"), new BigDecimal("18.00"))));

        // Act
        var invDto = service.createInvoiceFromConfirmation(conf.id(), invoiceReq, TEST_USER_ID);

        // Assert
        assertThat(invDto.id()).isNotNull();
        assertThat(invDto.docNo()).startsWith("SI");
        assertThat(invDto.invoiceKind()).isEqualTo("SERVICE");
        assertThat(invDto.serviceConfirmationId()).isEqualTo(conf.id());
        assertThat(invDto.customerId()).isEqualTo(customer.getId());
        // grandTotal = 10 * 500 * (1 + 18/100) = 5000 + 900 = 5900
        assertThat(invDto.grandTotal()).isEqualByComparingTo(new BigDecimal("5900.0000"));
        assertThat(invDto.items()).hasSize(1);
        assertThat(invDto.items().get(0).description()).isEqualTo("Installation Labour");
    }
}

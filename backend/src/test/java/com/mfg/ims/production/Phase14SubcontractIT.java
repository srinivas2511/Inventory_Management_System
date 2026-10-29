package com.mfg.ims.production;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.production.jobwork.CreateJobWorkOrderRequest;
import com.mfg.ims.production.jobwork.JobWorkService;
import com.mfg.ims.production.serviceorder.CreateServiceOrderRequest;
import com.mfg.ims.production.serviceorder.ServiceOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Phase 14 — Sub-contract job work and service orders.
 *
 * <p>P14-1 Create JWO; P14-2 List JWOs; P14-3 Issue JWO; P14-4 Create service order; P14-5 List service orders.
 */
@Transactional
class Phase14SubcontractIT extends AbstractIntegrationTest {

    @Autowired JobWorkService jwoService;
    @Autowired ServiceOrderService soService;
    @Autowired TestData td;

    private static final Long TEST_USER_ID = 9014L;

    @BeforeEach
    void setUpSecurityContext() {
        var actor = new CachedUser(TEST_USER_ID, "p14-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("JWO_VIEW", "JWO_CREATE", "JWO_ISSUE", "JWO_RECEIVE",
                        "SERVICE_ORDER_VIEW", "SERVICE_ORDER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of(
                        new SimpleGrantedAuthority("JWO_VIEW"),
                        new SimpleGrantedAuthority("JWO_CREATE"),
                        new SimpleGrantedAuthority("JWO_ISSUE"),
                        new SimpleGrantedAuthority("JWO_RECEIVE"),
                        new SimpleGrantedAuthority("SERVICE_ORDER_VIEW"),
                        new SimpleGrantedAuthority("SERVICE_ORDER_CREATE"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void p14_1_create_job_work_order() {
        var supplier = td.supplier();
        var req = new CreateJobWorkOrderRequest(
                LocalDate.now(), supplier.getId(), "CH-001",
                LocalDate.now(), LocalDate.now().plusDays(7),
                new BigDecimal("500.00"), "Test JWO", List.of());

        var jwo = jwoService.create(req, TEST_USER_ID);

        assertThat(jwo.id()).isNotNull();
        assertThat(jwo.docNo()).startsWith("JWO");
        assertThat(jwo.status()).isEqualTo("DRAFT");
        assertThat(jwo.vendorId()).isEqualTo(supplier.getId());
    }

    @Test
    void p14_2_list_job_work_orders() {
        var supplier = td.supplier();
        var req = new CreateJobWorkOrderRequest(
                LocalDate.now(), supplier.getId(), null, null, null, null, null, List.of());
        jwoService.create(req, TEST_USER_ID);

        var list = jwoService.list();

        assertThat(list).isNotEmpty();
    }

    @Test
    void p14_3_issue_job_work_order() {
        var supplier = td.supplier();
        var created = jwoService.create(new CreateJobWorkOrderRequest(
                LocalDate.now(), supplier.getId(), null, null, null, null, null, List.of()), TEST_USER_ID);

        var issued = jwoService.issue(created.id(), TEST_USER_ID);

        assertThat(issued.status()).isEqualTo("ISSUED");
        assertThat(issued.issueDate()).isNotNull();
    }

    @Test
    void p14_4_create_service_order() {
        var req = new CreateServiceOrderRequest(
                LocalDate.now(), "INWARD", null, null,
                "Annual maintenance", LocalDate.now().plusDays(3),
                new BigDecimal("2000.00"), new BigDecimal("360.00"), null);

        var so = soService.create(req, TEST_USER_ID);

        assertThat(so.id()).isNotNull();
        assertThat(so.docNo()).startsWith("SVO");
        assertThat(so.status()).isEqualTo("DRAFT");
        assertThat(so.serviceKind()).isEqualTo("INWARD");
        assertThat(so.grandTotal()).isEqualByComparingTo(new BigDecimal("2360.00"));
    }

    @Test
    void p14_5_list_service_orders() {
        var req = new CreateServiceOrderRequest(
                LocalDate.now(), "OUTWARD", null, null, "Outward service", null, null, null, null);
        soService.create(req, TEST_USER_ID);

        var list = soService.list();

        assertThat(list).isNotEmpty();
    }
}

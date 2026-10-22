package com.mfg.ims.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Starts the application the way a developer does (dev profile, demo load on) against its own empty PostgreSQL, so
 * the demo data never leaks into the shared database the other integration tests use. The loader runs at startup;
 * the tests then check what a person would find: the documented admin can log in, the whole flow from material
 * issue to dispatch completed, and loading again changes nothing.
 */
@SpringBootTest(properties = "ims.demo.load-data=true")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@Testcontainers(disabledWithoutDocker = true)
class DemoDataLoaderIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ims").withUsername("ims").withPassword("ims");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        synchronized (postgres) {
            if (!postgres.isRunning()) {
                postgres.start();
            }
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataLoader loader;
    @Autowired com.mfg.ims.insight.dashboard.DashboardTrendService trendService;

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void theDocumentedAdminCanLogIn() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM ims.users WHERE username = 'admin'")).isEqualTo(1);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void theDesignRoleUsersExistWithTheInitialPasswordAndMustChangeIt() throws Exception {
        // D-01, DESIGN section 11.2: one demo user per role, initial password Demo@12345!, change forced at first login
        assertThat(count("SELECT COUNT(*) FROM ims.users WHERE username IN ('engineer1','prodmgr1','supervisor1',"
                + "'operator1','operator2','quality1','storemgr1','storeop1','purchase1','sales1','dispatch1',"
                + "'maint1','mgmt1','finance1') AND must_change_password")).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM ims.users u JOIN ims.user_roles ur ON ur.user_id = u.id "
                + "JOIN ims.roles r ON r.id = ur.role_id WHERE u.username = 'storemgr1' AND r.code = 'STORE_MANAGER'"))
                .isEqualTo(1);
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"purchase1\",\"password\":\"Demo@12345!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("PURCHASE_MANAGER"));
    }

    @Test
    void aWrongPasswordIsStillRefused() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theWholeFlowFromMaterialIssueToDispatchCompleted() {
        long orders = count("SELECT COUNT(*) FROM ims.production_orders WHERE remarks LIKE 'Demo production run%'");
        assertThat(orders).isEqualTo(1);

        // every operation processed the 100 pieces: the first one started from the planned quantity
        assertThat(count("""
                SELECT COUNT(*) FROM ims.production_operations op
                  JOIN ims.production_orders po ON po.id = op.production_order_id
                 WHERE po.remarks LIKE 'Demo production run%' AND op.good_qty = 100 AND op.status = 'COMPLETED'
                """)).isEqualTo(3);
        assertThat(count("""
                SELECT COUNT(*) FROM ims.production_operations op
                  JOIN ims.production_orders po ON po.id = op.production_order_id
                 WHERE po.remarks LIKE 'Demo production run%' AND op.sequence_no = 10 AND op.input_qty = 100
                """)).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM ims.fg_batches WHERE quality_status = 'APPROVED'")).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ims.sales_orders WHERE notes LIKE 'Demo sales order%'")).isEqualTo(1);
        // the reservation was consumed by the dispatch, which posted the stock out and raised the AR invoice
        assertThat(count("SELECT COUNT(*) FROM ims.sales_reservations WHERE status = 'ACTIVE'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM ims.sales_reservations WHERE status = 'USED'")).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM ims.dispatches d
                  JOIN ims.sales_orders so ON so.id = d.sales_order_id
                 WHERE so.notes LIKE 'Demo sales order%'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM ims.ar_invoices i
                  JOIN ims.dispatches d ON d.id = i.dispatch_id
                  JOIN ims.sales_orders so ON so.id = d.sales_order_id
                 WHERE so.notes LIKE 'Demo sales order%' AND i.total_amount > 0
                """)).isEqualTo(1);
    }

    @Test
    void theDemoDataShowsAnOpenLowStockAlert() {
        // DESIGN 11.4: the alerts screen is not empty after a demo load. The scheduled sweep ran once at startup,
        // before any demo data existed, so the loader seeds a material with no stock and runs the sweep itself.
        assertThat(count("""
                SELECT COUNT(*) FROM ims.alerts a JOIN ims.materials m ON m.id = a.entity_id
                 WHERE a.alert_type = 'LOW_STOCK' AND a.entity_type = 'MATERIAL' AND a.resolved_at IS NULL
                   AND m.material_code = 'DEMO-WR-LOWSTK'
                """)).isEqualTo(1);
    }

    @Test
    void theDemoDataAlsoShowsALateProductionRunAndAWaitingInspection() {
        // DESIGN 11.4 names three alerts: low stock, production delay and pending inspection
        assertThat(count("""
                SELECT COUNT(*) FROM ims.alerts a JOIN ims.production_orders po ON po.id = a.entity_id
                 WHERE a.alert_type = 'PRODUCTION_DELAY' AND a.entity_type = 'PRODUCTION_ORDER'
                   AND a.resolved_at IS NULL AND po.status = 'IN_PROGRESS'
                   AND po.expected_completion_date < current_date
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM ims.alerts a JOIN ims.quality_inspections q ON q.id = a.entity_id
                 WHERE a.alert_type = 'PENDING_INSPECTION' AND a.entity_type = 'QUALITY_INSPECTION'
                   AND a.resolved_at IS NULL AND q.status = 'PENDING'
                """)).isEqualTo(1);
    }

    @Test
    void theDashboardChartsHaveDataAfterTheDemoLoad() {
        // UAT INS-01/INS-02: throughput and inventory value series come from live data, not from stale views
        var trends = trendService.trends(6);
        assertThat(trends.production()).isNotEmpty();
        assertThat(trends.production()).anyMatch(m -> m.goodQty().signum() > 0);
        assertThat(trends.inventoryValue()).isNotEmpty();
        assertThat(trends.inventoryValue()).anyMatch(m -> m.closingValue().signum() > 0);
        assertThat(trends.inventoryValue()).extracting(m -> m.month()).isSorted();
        assertThat(trends.production()).extracting(m -> m.month()).isSorted();
        // the request is clamped: a silly month count cannot read the whole ledger
        assertThat(trendService.trends(1000).inventoryValue().size()).isLessThanOrEqualTo(24);
        assertThat(trendService.trends(0).inventoryValue().size()).isLessThanOrEqualTo(1);
    }

    @Test
    void loadingASecondTimeChangesNothing() {
        long users = count("SELECT COUNT(*) FROM ims.users");
        long orders = count("SELECT COUNT(*) FROM ims.production_orders");
        long dispatches = count("SELECT COUNT(*) FROM ims.dispatches");
        long transactions = count("SELECT COUNT(*) FROM ims.inventory_transactions");
        long suppliers = count("SELECT COUNT(*) FROM ims.suppliers WHERE supplier_code = 'DEMO-KRISHNA'");

        loader.load();

        assertThat(count("SELECT COUNT(*) FROM ims.users")).isEqualTo(users);
        assertThat(count("SELECT COUNT(*) FROM ims.production_orders")).isEqualTo(orders);
        assertThat(count("SELECT COUNT(*) FROM ims.dispatches")).isEqualTo(dispatches);
        assertThat(count("SELECT COUNT(*) FROM ims.inventory_transactions")).isEqualTo(transactions);
        assertThat(count("SELECT COUNT(*) FROM ims.suppliers WHERE supplier_code = 'DEMO-KRISHNA'")).isEqualTo(suppliers);
    }
}

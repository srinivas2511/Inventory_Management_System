package com.springmfg.ims.config;

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

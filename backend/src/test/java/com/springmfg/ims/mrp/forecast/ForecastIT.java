package com.springmfg.ims.mrp.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastResult;
import com.springmfg.ims.mrp.forecast.ForecastTypes.MonthlyQuantity;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ProductHistory;
import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.mrp.dto.SaveDemandForecastRequest;
import com.springmfg.ims.mrp.service.DemandForecastService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * The AI forecast seam (R19-3) end to end: a stub model behind the real adapter and service, writing into the real
 * {@code demand_forecasts} table that MRP reads. (The real HTTP client is covered by its unconfigured default.)
 */
class ForecastIT extends AbstractIntegrationTest {

    @Autowired ManualDemandForecastProvider manual;
    @Autowired DemandForecastService forecastService;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired AuditService audit;
    @Autowired ForecastService realService;
    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;

    /** A model that returns whatever it is told to. */
    static class StubModel implements AiForecastClient {
        List<ForecastPoint> next = new ArrayList<>();
        int calls;

        @Override public boolean isConfigured() { return true; }
        @Override public String endpointHost() { return "model.test"; }
        @Override public List<ForecastPoint> forecast(List<ProductHistory> history, int horizonDays) throws IOException {
            calls++;
            return next;
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private ForecastService serviceWith(StubModel model, Product product) {
        SalesHistorySource history = months -> List.of(new ProductHistory(product.getId(), product.getProductCode(),
                List.of(new MonthlyQuantity("2026-08", new BigDecimal("90")), new MonthlyQuantity("2026-09", new BigDecimal("110")))));
        return new ForecastService(manual, new AiForecastAdapter(model, history), forecastService, namedJdbc, audit);
    }

    private BigDecimal quantityOn(Product product, LocalDate date) {
        List<BigDecimal> rows = jdbc.queryForList("SELECT quantity FROM ims.demand_forecasts WHERE product_id = ? AND target_date = ?",
                BigDecimal.class, product.getId(), date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String notesOn(Product product, LocalDate date) {
        return jdbc.queryForObject("SELECT notes FROM ims.demand_forecasts WHERE product_id = ? AND target_date = ?",
                String.class, product.getId(), date);
    }

    @Test
    void aiForecastsAreValidatedAndStoredWhereMrpReadsThem() {
        Product product = td.product();
        LocalDate d1 = LocalDate.now().plusDays(10);
        LocalDate d2 = LocalDate.now().plusDays(40);
        StubModel model = new StubModel();
        model.next = List.of(
                new ForecastPoint(product.getId(), d1, new BigDecimal("120.5")),
                new ForecastPoint(product.getId(), d2, new BigDecimal("80")),
                new ForecastPoint(999_999_999L, LocalDate.now().plusDays(5), new BigDecimal("10")),     // not in the history
                new ForecastPoint(product.getId(), LocalDate.now().plusDays(200), new BigDecimal("10"))); // beyond the horizon

        ForecastResult result = serviceWith(model, product).generate(90);

        assertThat(model.calls).isEqualTo(1);
        assertThat(result.provider()).isEqualTo("AI");
        assertThat(result.aiConfigured()).isTrue();
        assertThat(result.proposed()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.rejected()).isEqualTo(2);
        assertThat(quantityOn(product, d1)).isEqualByComparingTo("120.500");
        assertThat(quantityOn(product, d2)).isEqualByComparingTo("80.000");
        assertThat(notesOn(product, d1)).startsWith("AI forecast");

        // MRP reads demand_forecasts; the default provider is the same table
        assertThat(manual.forecast(90)).anySatisfy(p -> {
            assertThat(p.productId()).isEqualTo(product.getId());
            assertThat(p.targetDate()).isEqualTo(d1);
            assertThat(p.quantity()).isEqualByComparingTo("120.500");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'AI_FORECAST_IMPORT'",
                Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aRerunRefreshesAiRowsButNeverOverwritesAPlannersForecast() {
        Product product = td.product();
        LocalDate d1 = LocalDate.now().plusDays(10);
        LocalDate d3 = LocalDate.now().plusDays(20);
        StubModel model = new StubModel();
        ForecastService svc = serviceWith(model, product);
        model.next = List.of(new ForecastPoint(product.getId(), d1, new BigDecimal("100")));
        assertThat(svc.generate(90).created()).isEqualTo(1);

        forecastService.create(new SaveDemandForecastRequest(product.getId(), d3, new BigDecimal("77"), "planner estimate"));
        model.next = List.of(
                new ForecastPoint(product.getId(), d1, new BigDecimal("200")),   // an AI row: refreshed
                new ForecastPoint(product.getId(), d3, new BigDecimal("5")));    // a planner's row: kept

        ForecastResult second = svc.generate(90);

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.keptManual()).isEqualTo(1);
        assertThat(quantityOn(product, d1)).isEqualByComparingTo("200");
        assertThat(quantityOn(product, d3)).isEqualByComparingTo("77");
        assertThat(notesOn(product, d3)).isEqualTo("planner estimate");
    }

    @Test
    void anOutOfRangeHorizonIsClampedNotRejected() {
        Product product = td.product();
        StubModel model = new StubModel();
        ForecastService svc = serviceWith(model, product);
        model.next = List.of(new ForecastPoint(product.getId(), LocalDate.now().plusDays(1), new BigDecimal("1")));

        assertThat(svc.generate(0).proposed()).as("a horizon of 0 becomes 1 day").isEqualTo(1);
        assertThat(svc.generate(100_000).provider()).isEqualTo("AI");
        assertThat(svc.generate(null).provider()).isEqualTo("AI");
    }

    @Test
    void withoutAnEndpointTheDefaultIsManualAndNothingIsCreated() {
        var info = realService.info();
        assertThat(info.active()).isEqualTo("MANUAL");
        assertThat(info.aiConfigured()).isFalse();
        assertThat(info.aiEndpointHost()).isNull();

        Integer before = jdbc.queryForObject("SELECT count(*) FROM ims.demand_forecasts", Integer.class);
        ForecastResult result = realService.generate(30);
        assertThat(result.provider()).isEqualTo("MANUAL");
        assertThat(result.created()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.demand_forecasts", Integer.class)).isEqualTo(before);
    }

    private static UsernamePasswordAuthenticationToken caller(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9102L, "forecast-it", true, 1, Set.of(), Set.of(authorities)), null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void theEndpointsNeedDemandPlan() throws Exception {
        mockMvc.perform(get("/api/analytics/forecast/provider").with(authentication(caller("DEMAND_PLAN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value("MANUAL"))
                .andExpect(jsonPath("$.aiConfigured").value(false));
        mockMvc.perform(post("/api/analytics/forecast/generate").with(authentication(caller("DEMAND_PLAN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.provider").value("MANUAL"));
        mockMvc.perform(get("/api/analytics/forecast/provider").with(authentication(caller("ANALYTICS_VIEW"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/analytics/forecast/generate").with(authentication(caller("MRP_VIEW"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/analytics/forecast/generate")).andExpect(status().isUnauthorized());
    }
}

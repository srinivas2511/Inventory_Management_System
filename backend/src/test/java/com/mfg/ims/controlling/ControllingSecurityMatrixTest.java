package com.mfg.ims.controlling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.mfg.ims.common.exception.GlobalExceptionHandler;
import com.mfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.mfg.ims.config.CorrelationIdFilter;
import com.mfg.ims.config.JwtAuthenticationFilter;
import com.mfg.ims.config.ProblemDetailSecurityHandlers;
import com.mfg.ims.config.SecurityConfig;
import com.mfg.ims.controlling.service.CostCentreService;
import com.mfg.ims.controlling.service.ProductCostingService;
import com.mfg.ims.controlling.service.ProductionOrderVarianceService;
import com.mfg.ims.controlling.service.ProfitCentreService;
import com.mfg.ims.controlling.web.CostCentreController;
import com.mfg.ims.controlling.web.ProductCostingController;
import com.mfg.ims.controlling.web.ProfitCentreController;
import com.mfg.ims.controlling.web.VarianceController;

/**
 * 403 matrix for every controlling endpoint (R10-5.2). Web slice only, so it runs without Docker.
 * Each endpoint must reject anonymous callers (401), reject callers holding any other controlling or
 * finance authority (403), and accept the one authority it declares.
 */
@WebMvcTest(controllers = { CostCentreController.class, ProfitCentreController.class,
        ProductCostingController.class, VarianceController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class ControllingSecurityMatrixTest {

    private static final String VIEW = "CONTROLLING_VIEW";
    private static final String MANAGE = "CONTROLLING_MANAGE";
    private static final String RELEASE = "COST_ESTIMATE_RELEASE";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, MANAGE, RELEASE, "GL_VIEW", "FINANCE_REPORT", "REPORT_VIEW");

    private static final String COST_CENTRE_BODY =
            "{\"costCentreCode\":\"CC-T\",\"name\":\"Test\",\"labourRatePerHour\":1,\"machineRatePerHour\":1,\"overheadPct\":1}";
    private static final String PROFIT_CENTRE_BODY = "{\"profitCentreCode\":\"PC-T\",\"name\":\"Test\"}";
    private static final String BUDGET_BODY = "{\"glAccountId\":1,\"periodId\":1,\"budgetedAmount\":10}";
    private static final String ESTIMATE_BODY = "{\"productId\":1,\"bomId\":1}";

    record Endpoint(HttpMethod method, String template, String query, String authority, String body) {
        String url() {
            return query == null ? template : template + "?" + query;
        }

        @Override
        public String toString() {
            return method + " " + url() + " [" + authority + "]";
        }
    }

    static List<Endpoint> endpoints() {
        String cc = "/api/controlling/cost-centres";
        String pc = "/api/controlling/profit-centres";
        String est = "/api/controlling/cost-estimates";
        String var = "/api/controlling/variances";
        return List.of(
                new Endpoint(HttpMethod.GET, cc, null, VIEW, null),
                new Endpoint(HttpMethod.GET, cc + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, cc, null, MANAGE, COST_CENTRE_BODY),
                new Endpoint(HttpMethod.PUT, cc + "/{id}", null, MANAGE, COST_CENTRE_BODY),
                new Endpoint(HttpMethod.POST, cc + "/{id}/activate", null, MANAGE, null),
                new Endpoint(HttpMethod.POST, cc + "/{id}/deactivate", null, MANAGE, null),
                new Endpoint(HttpMethod.GET, cc + "/{id}/budgets", "fiscalYear=2026", VIEW, null),
                new Endpoint(HttpMethod.PUT, cc + "/{id}/budgets", null, MANAGE, BUDGET_BODY),
                new Endpoint(HttpMethod.GET, pc, null, VIEW, null),
                new Endpoint(HttpMethod.GET, pc + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, pc, null, MANAGE, PROFIT_CENTRE_BODY),
                new Endpoint(HttpMethod.PUT, pc + "/{id}", null, MANAGE, PROFIT_CENTRE_BODY),
                new Endpoint(HttpMethod.POST, pc + "/{id}/activate", null, MANAGE, null),
                new Endpoint(HttpMethod.POST, pc + "/{id}/deactivate", null, MANAGE, null),
                new Endpoint(HttpMethod.GET, est, null, VIEW, null),
                new Endpoint(HttpMethod.GET, est + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, est, null, MANAGE, ESTIMATE_BODY),
                new Endpoint(HttpMethod.POST, est + "/{id}/release", null, RELEASE, null),
                new Endpoint(HttpMethod.GET, var, null, VIEW, null),
                new Endpoint(HttpMethod.GET, var + "/{productionOrderId}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, var + "/{productionOrderId}/calculate", null, MANAGE, null));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean CostCentreService costCentreService;
    @MockBean ProfitCentreService profitCentreService;
    @MockBean ProductCostingService productCostingService;
    @MockBean ProductionOrderVarianceService varianceService;

    @Autowired MockMvc mvc;

    @BeforeEach
    void stubJwtFilterAsPassThrough() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private org.springframework.test.web.servlet.ResultActions call(Endpoint e, String authority) throws Exception {
        var builder = request(e.method(), e.url(), 1L).with(csrf());
        if (authority != null) {
            builder = builder.with(user("tester").authorities(new SimpleGrantedAuthority(authority)));
        }
        if (e.body() != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(e.body());
        }
        return mvc.perform(builder);
    }

    @ParameterizedTest(name = "{0} - anonymous gets 401")
    @MethodSource("endpointStream")
    void anonymousIsUnauthorized(Endpoint e) throws Exception {
        call(e, null).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} - any other authority gets 403")
    @MethodSource("endpointStream")
    void otherAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (other.equals(e.authority())) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority is accepted")
    @MethodSource("endpointStream")
    void declaredAuthorityIsAccepted(Endpoint e) throws Exception {
        call(e, e.authority()).andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryControllingEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(CostCentreController.class, ProfitCentreController.class,
                ProductCostingController.class, VarianceController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0];
            for (Method m : c.getDeclaredMethods()) {
                String key = key(m, base);
                if (key != null) declared.add(key);
            }
        }
        Set<String> covered = new HashSet<>();
        for (Endpoint e : endpoints()) {
            covered.add(e.method() + " " + e.template().replaceAll("\\{[^}]+}", "{}"));
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(declared);
    }

    private static String key(Method m, String base) {
        List<String> parts = new ArrayList<>();
        String verb = null;
        if (m.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            parts.addAll(List.of(m.getAnnotation(GetMapping.class).value()));
        } else if (m.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            parts.addAll(List.of(m.getAnnotation(PostMapping.class).value()));
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            parts.addAll(List.of(m.getAnnotation(PutMapping.class).value()));
        }
        if (verb == null) return null;
        String path = base + (parts.isEmpty() ? "" : parts.get(0));
        return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}

package com.springmfg.ims.mrp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.springmfg.ims.common.exception.GlobalExceptionHandler;
import com.springmfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.springmfg.ims.config.CorrelationIdFilter;
import com.springmfg.ims.config.JwtAuthenticationFilter;
import com.springmfg.ims.config.ProblemDetailSecurityHandlers;
import com.springmfg.ims.config.SecurityConfig;
import com.springmfg.ims.mrp.service.DemandForecastService;
import com.springmfg.ims.mrp.service.MrpProposalService;
import com.springmfg.ims.mrp.service.MrpRunService;
import com.springmfg.ims.mrp.service.PlanningParamService;
import com.springmfg.ims.mrp.web.DemandForecastController;
import com.springmfg.ims.mrp.web.MrpProposalController;
import com.springmfg.ims.mrp.web.MrpRunController;
import com.springmfg.ims.mrp.web.PlanningParamController;

/**
 * 403 matrix for every MRP endpoint (R11-3.3). Web slice only, so it runs without Docker. Each endpoint must
 * reject anonymous callers (401), reject callers holding any other MRP authority (403) and accept the one
 * authority it declares.
 */
@WebMvcTest(controllers = { MrpRunController.class, MrpProposalController.class, DemandForecastController.class,
        PlanningParamController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class MrpSecurityMatrixTest {

    private static final String VIEW = "MRP_VIEW";
    private static final String RUN = "MRP_RUN";
    private static final String PLAN = "DEMAND_PLAN";
    private static final String BUY = "PURCHASE_PROPOSAL";
    private static final String MAKE = "PRODUCTION_PROPOSAL";
    private static final List<String> ALL_AUTHORITIES =
            List.of(VIEW, RUN, PLAN, BUY, MAKE, "CONTROLLING_VIEW", "PURCHASE_APPROVE", "REPORT_VIEW");

    private static final String FORECAST_BODY = "{\"productId\":1,\"targetDate\":\"2026-12-01\",\"quantity\":5}";
    private static final String PARAM_BODY = "{\"productId\":1,\"planningStrategy\":\"MTS\",\"lotSizeRule\":\"EXACT\"}";
    private static final String DISCARD_BODY = "{\"reason\":\"not needed\"}";

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
        String runs = "/api/mrp/runs";
        String props = "/api/mrp/proposals";
        String fc = "/api/mrp/forecasts";
        String pp = "/api/mrp/planning-params";
        return List.of(
                new Endpoint(HttpMethod.GET, runs, null, VIEW, null),
                new Endpoint(HttpMethod.GET, runs + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, runs, null, RUN, "{}"),
                new Endpoint(HttpMethod.GET, runs + "/{id}/proposals", null, VIEW, null),
                new Endpoint(HttpMethod.POST, runs + "/{id}/convert-all", "type=PURCHASE", BUY, null),
                new Endpoint(HttpMethod.POST, runs + "/{id}/convert-all", "type=PRODUCTION", MAKE, null),
                new Endpoint(HttpMethod.GET, props, null, VIEW, null),
                new Endpoint(HttpMethod.GET, props + "/{id}", null, VIEW, null),
                new Endpoint(HttpMethod.POST, props + "/{id}/convert-to-pr", null, BUY, null),
                new Endpoint(HttpMethod.POST, props + "/{id}/convert-to-production-order", null, MAKE, null),
                new Endpoint(HttpMethod.POST, props + "/{id}/discard", null, RUN, DISCARD_BODY),
                new Endpoint(HttpMethod.GET, fc, null, VIEW, null),
                new Endpoint(HttpMethod.POST, fc, null, PLAN, FORECAST_BODY),
                new Endpoint(HttpMethod.PUT, fc + "/{id}", null, PLAN, FORECAST_BODY),
                new Endpoint(HttpMethod.DELETE, fc + "/{id}", null, PLAN, null),
                new Endpoint(HttpMethod.GET, pp, null, VIEW, null),
                new Endpoint(HttpMethod.PUT, pp, null, PLAN, PARAM_BODY),
                new Endpoint(HttpMethod.DELETE, pp + "/{id}", null, PLAN, null));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean MrpRunService runService;
    @MockBean MrpProposalService proposalService;
    @MockBean DemandForecastService forecastService;
    @MockBean PlanningParamService planningParamService;

    @Autowired MockMvc mvc;

    @BeforeEach
    void stubJwtFilterAsPassThrough() throws Exception {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    private ResultActions call(Endpoint e, String authority) throws Exception {
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
    void matrixCoversEveryMrpEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(MrpRunController.class, MrpProposalController.class,
                DemandForecastController.class, PlanningParamController.class)) {
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
        String verb = null;
        String[] paths = {};
        if (m.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            paths = m.getAnnotation(GetMapping.class).value();
        } else if (m.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            paths = m.getAnnotation(PostMapping.class).value();
        } else if (m.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            paths = m.getAnnotation(PutMapping.class).value();
        } else if (m.isAnnotationPresent(DeleteMapping.class)) {
            verb = "DELETE";
            paths = m.getAnnotation(DeleteMapping.class).value();
        }
        if (verb == null) return null;
        String path = base + (paths.length == 0 ? "" : paths[0]);
        return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}

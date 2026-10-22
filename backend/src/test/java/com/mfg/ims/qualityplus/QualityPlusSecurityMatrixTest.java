package com.mfg.ims.qualityplus;

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

import com.mfg.ims.common.exception.GlobalExceptionHandler;
import com.mfg.ims.common.idempotency.IdempotencyKeyRepository;
import com.mfg.ims.config.CorrelationIdFilter;
import com.mfg.ims.config.JwtAuthenticationFilter;
import com.mfg.ims.config.ProblemDetailSecurityHandlers;
import com.mfg.ims.config.SecurityConfig;
import com.mfg.ims.qualityplus.service.CapaService;
import com.mfg.ims.qualityplus.service.ComplaintService;
import com.mfg.ims.qualityplus.service.FaiService;
import com.mfg.ims.qualityplus.service.SpcService;
import com.mfg.ims.qualityplus.web.CapaController;
import com.mfg.ims.qualityplus.web.ComplaintController;
import com.mfg.ims.qualityplus.web.FaiController;
import com.mfg.ims.qualityplus.web.SpcController;

/**
 * 403 matrix for every Advanced Quality endpoint (R16). Web slice only, so it runs without Docker. Each endpoint must
 * reject anonymous callers (401), reject callers holding only authorities it does not declare (403) and accept each
 * authority it does declare. A final test fails when a controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { SpcController.class, CapaController.class, ComplaintController.class,
        FaiController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class QualityPlusSecurityMatrixTest {

    private static final String VIEW = "QUALITY_VIEW";
    private static final String INSPECT = "QUALITY_INSPECT";
    private static final String APPROVE = "QUALITY_APPROVE";
    private static final String SPC = "SPC_MANAGE";
    private static final String CAPA = "CAPA_MANAGE";
    private static final String CAPA_CLOSE = "CAPA_CLOSE";
    private static final String COMPLAINT = "COMPLAINT_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, INSPECT, APPROVE, SPC, CAPA, CAPA_CLOSE,
            COMPLAINT, "PLM_VIEW", "ECO_CREATE", "HR_VIEW", "CRM_VIEW", "REPORT_VIEW", "USER_UPDATE",
            "PRODUCT_UPDATE", "TRACEABILITY_VIEW");

    private static final String CHART_BODY = "{\"productId\":1,\"parameterName\":\"Wire diameter\",\"chartType\":\"XBAR_R\"}";
    private static final String CAPA_BODY = "{\"title\":\"Coil defects\",\"sourceType\":\"INTERNAL_AUDIT\"}";
    private static final String ADVANCE_BODY = "{\"target\":\"ROOT_CAUSE_ANALYSIS\"}";
    private static final String CLOSE_BODY = "{\"verificationNotes\":\"effective\"}";
    private static final String COMPLAINT_BODY =
            "{\"customerId\":1,\"complaintDate\":\"2026-10-04\",\"category\":\"OTHER\",\"description\":\"Too soft\"}";
    private static final String TRANSITION_BODY = "{\"target\":\"IN_PROGRESS\"}";

    record Endpoint(HttpMethod method, String template, Set<String> authorities, String body) {
        @Override
        public String toString() {
            return method + " " + template + " " + authorities;
        }
    }

    private static Endpoint ep(HttpMethod m, String template, String body, String... authorities) {
        return new Endpoint(m, template, Set.of(authorities), body);
    }

    static List<Endpoint> endpoints() {
        String spc = "/api/spc/charts";
        String capa = "/api/capa";
        String cmp = "/api/complaints";
        String fai = "/api/fai";
        return List.of(
                ep(HttpMethod.GET, spc, null, VIEW, INSPECT),
                ep(HttpMethod.GET, spc + "/{id}", null, VIEW, INSPECT),
                ep(HttpMethod.POST, spc, CHART_BODY, SPC),
                ep(HttpMethod.PUT, spc + "/{id}", "{}", SPC),
                ep(HttpMethod.POST, spc + "/{id}/points", "{\"values\":[1.5]}", INSPECT),
                ep(HttpMethod.POST, spc + "/{id}/recalculate-limits", null, SPC),
                ep(HttpMethod.GET, spc + "/{id}/capability", null, VIEW, INSPECT),

                ep(HttpMethod.GET, capa, null, VIEW, CAPA),
                ep(HttpMethod.GET, capa + "/{id}", null, VIEW, CAPA),
                ep(HttpMethod.POST, capa, CAPA_BODY, CAPA),
                ep(HttpMethod.PUT, capa + "/{id}", CAPA_BODY, CAPA),
                ep(HttpMethod.POST, capa + "/{id}/advance", ADVANCE_BODY, CAPA),
                ep(HttpMethod.POST, capa + "/{id}/close", CLOSE_BODY, CAPA_CLOSE),
                ep(HttpMethod.POST, capa + "/from-rejection/{id}", null, CAPA),
                ep(HttpMethod.POST, capa + "/from-spc-point/{id}", null, CAPA),
                ep(HttpMethod.POST, capa + "/from-complaint/{id}", null, CAPA),

                ep(HttpMethod.GET, cmp, null, VIEW, COMPLAINT),
                ep(HttpMethod.GET, cmp + "/lookups/customers", null, COMPLAINT),
                ep(HttpMethod.GET, cmp + "/{id}", null, VIEW, COMPLAINT),
                ep(HttpMethod.GET, cmp + "/{id}/trace", null, VIEW, COMPLAINT),
                ep(HttpMethod.POST, cmp, COMPLAINT_BODY, COMPLAINT),
                ep(HttpMethod.PUT, cmp + "/{id}", COMPLAINT_BODY, COMPLAINT),
                ep(HttpMethod.POST, cmp + "/{id}/transition", TRANSITION_BODY, COMPLAINT),

                ep(HttpMethod.GET, fai, null, VIEW, INSPECT),
                ep(HttpMethod.GET, fai + "/{id}", null, VIEW, INSPECT),
                ep(HttpMethod.GET, fai + "/{id}/report", null, VIEW, INSPECT),
                ep(HttpMethod.POST, fai, "{\"productId\":1}", INSPECT),
                ep(HttpMethod.POST, fai + "/{id}/start", "{\"productionOrderId\":1}", INSPECT),
                ep(HttpMethod.POST, fai + "/{id}/results",
                        "{\"values\":[{\"itemId\":1,\"actualValue\":1.5}]}", INSPECT),
                ep(HttpMethod.POST, fai + "/{id}/complete", null, APPROVE));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    static Stream<Object[]> declaredAuthorityStream() {
        return endpoints().stream().flatMap(e -> e.authorities().stream().map(a -> new Object[] { e, a }));
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean SpcService spcService;
    @MockBean CapaService capaService;
    @MockBean ComplaintService complaintService;
    @MockBean FaiService faiService;

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
        var builder = request(e.method(), e.template(), 1L, 1L).with(csrf());
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

    @ParameterizedTest(name = "{0} - any undeclared authority gets 403")
    @MethodSource("endpointStream")
    void undeclaredAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority {1} is accepted")
    @MethodSource("declaredAuthorityStream")
    void declaredAuthorityIsAccepted(Endpoint e, String authority) throws Exception {
        call(e, authority).andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryAdvancedQualityEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(SpcController.class, CapaController.class, ComplaintController.class,
                FaiController.class)) {
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

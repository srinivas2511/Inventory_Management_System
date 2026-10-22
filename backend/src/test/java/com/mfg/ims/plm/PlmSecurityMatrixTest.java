package com.mfg.ims.plm;

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
import com.mfg.ims.plm.service.EcoService;
import com.mfg.ims.plm.service.ProductLifecycleService;
import com.mfg.ims.plm.web.EcoController;
import com.mfg.ims.plm.web.ProductLifecycleController;

/**
 * 403 matrix for every PLM endpoint (R15-3.2). Web slice only, so it runs without Docker. Each endpoint must reject
 * anonymous callers (401), reject callers holding only authorities it does not declare (403) and accept each
 * authority it does declare. A final test fails when a controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { EcoController.class, ProductLifecycleController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class PlmSecurityMatrixTest {

    private static final String VIEW = "PLM_VIEW";
    private static final String CREATE = "ECO_CREATE";
    private static final String APPROVE = "ECO_APPROVE";
    private static final String LIFECYCLE = "LIFECYCLE_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, CREATE, APPROVE, LIFECYCLE, "DRAWING_MANAGE",
            "SPECIFICATION_MANAGE", "BOM_VIEW", "BOM_APPROVE", "PRODUCT_UPDATE", "CRM_VIEW", "HR_VIEW",
            "REPORT_VIEW", "USER_UPDATE");

    private static final String ECO_BODY =
            "{\"title\":\"Wire change\",\"reason\":\"Supplier change\",\"changeCategory\":\"BOM_CHANGE\"}";
    private static final String REASON_BODY = "{\"reason\":\"needs rework\"}";
    private static final String TRANSITION_BODY = "{\"target\":\"PROTOTYPE\",\"reason\":\"prototype built\"}";

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
        String eco = "/api/ecos";
        String lc = "/api/product-lifecycle";
        return List.of(
                ep(HttpMethod.GET, eco, null, VIEW),
                ep(HttpMethod.GET, eco + "/{id}", null, VIEW),
                ep(HttpMethod.POST, eco, ECO_BODY, CREATE),
                ep(HttpMethod.PUT, eco + "/{id}", ECO_BODY, CREATE),
                ep(HttpMethod.POST, eco + "/{id}/submit", null, CREATE),
                ep(HttpMethod.POST, eco + "/{id}/return", REASON_BODY, APPROVE),
                ep(HttpMethod.POST, eco + "/{id}/approve", null, APPROVE),
                ep(HttpMethod.POST, eco + "/{id}/cancel", REASON_BODY, CREATE, APPROVE),
                ep(HttpMethod.POST, eco + "/{id}/implement", null, CREATE),

                ep(HttpMethod.GET, lc, null, VIEW),
                ep(HttpMethod.GET, lc + "/{productId}", null, VIEW),
                ep(HttpMethod.POST, lc + "/{productId}/transition", TRANSITION_BODY, LIFECYCLE));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    static Stream<Object[]> declaredAuthorityStream() {
        return endpoints().stream().flatMap(e -> e.authorities().stream().map(a -> new Object[] { e, a }));
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean EcoService ecoService;
    @MockBean ProductLifecycleService lifecycleService;

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
    void matrixCoversEveryPlmEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(EcoController.class, ProductLifecycleController.class)) {
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

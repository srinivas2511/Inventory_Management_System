package com.springmfg.ims.advmfg;

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
import com.springmfg.ims.maintenance.controller.MaintenanceController;
import com.springmfg.ims.maintenance.controller.SparePartController;
import com.springmfg.ims.maintenance.service.MaintenanceCostPoster;
import com.springmfg.ims.maintenance.service.MaintenanceCostService;
import com.springmfg.ims.maintenance.service.MaintenanceService;
import com.springmfg.ims.maintenance.service.SparePartService;
import com.springmfg.ims.oee.OeeController;
import com.springmfg.ims.oee.OeeService;
import com.springmfg.ims.scheduling.SchedulingController;
import com.springmfg.ims.scheduling.SchedulingService;

import jakarta.servlet.FilterChain;

/**
 * 403 matrix for every spare part, maintenance, OEE and scheduling endpoint (R18). Web slice only, so it runs without
 * Docker. Each endpoint must reject anonymous callers (401), reject callers holding any authority it does not
 * declare (403) and accept each authority it does declare. The role grants themselves are checked against the
 * real seeded roles by {@code RoleAccessIT}.
 */
@WebMvcTest(controllers = { SparePartController.class, MaintenanceController.class, OeeController.class,
        SchedulingController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class AdvancedMfgSecurityMatrixTest {

    private static final String SP_VIEW = "SPARE_PART_VIEW";
    private static final String SP_MANAGE = "SPARE_PART_MANAGE";
    private static final String SP_ISSUE = "SPARE_PART_ISSUE";
    private static final String M_VIEW = "MAINTENANCE_VIEW";
    private static final String M_MANAGE = "MAINTENANCE_MANAGE";
    private static final String COST_VIEW = "MAINTENANCE_COST_VIEW";
    private static final String COST_POST = "MAINTENANCE_COST_POST";
    private static final String OEE = "OEE_VIEW";
    private static final String S_VIEW = "SCHEDULE_VIEW";
    private static final String S_MANAGE = "SCHEDULE_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(SP_VIEW, SP_MANAGE, SP_ISSUE, M_VIEW, M_MANAGE,
            COST_VIEW, COST_POST, OEE, S_VIEW, S_MANAGE, "PRODUCTION_VIEW", "REPORT_VIEW");

    private static final String PART_BODY = "{\"sparePartCode\":\"SP1\",\"name\":\"Bearing\",\"uom\":\"PCS\","
            + "\"minStock\":0,\"reorderLevel\":0,\"standardCost\":0}";
    private static final String REQUEST_BODY =
            "{\"machineId\":1,\"requestType\":\"CORRECTIVE\",\"priority\":3,\"description\":\"x\"}";
    private static final String PM_BODY = "{\"machineId\":1,\"taskName\":\"Grease\",\"frequencyDays\":30}";
    private static final String ACTIVITY_BODY = "{\"description\":\"x\",\"durationMinutes\":10}";
    private static final String ISSUE_BODY = "{\"sparePartId\":1,\"warehouseId\":1,\"quantity\":1}";
    private static final String MOVE_BODY = "{\"machineId\":1,\"start\":\"2035-01-01T09:00:00Z\"}";

    record Endpoint(HttpMethod method, String template, String query, Set<String> authorities, String body) {
        String url() {
            return query == null ? template : template + "?" + query;
        }

        @Override
        public String toString() {
            return method + " " + url() + " " + authorities;
        }
    }

    private static Endpoint ep(HttpMethod method, String template, String query, String body, String... authorities) {
        return new Endpoint(method, template, query, Set.of(authorities), body);
    }

    static List<Endpoint> endpoints() {
        String sp = "/api/spare-parts";
        String m = "/api/maintenance";
        String oee = "/api/oee";
        String sch = "/api/scheduling";
        return List.of(
                ep(HttpMethod.GET, sp, null, null, SP_VIEW),
                ep(HttpMethod.GET, sp + "/{id}", null, null, SP_VIEW),
                ep(HttpMethod.POST, sp, null, PART_BODY, SP_MANAGE),
                ep(HttpMethod.PUT, sp + "/{id}", null, PART_BODY, SP_MANAGE),
                ep(HttpMethod.DELETE, sp + "/{id}", null, null, SP_MANAGE),

                ep(HttpMethod.GET, m + "/requests", null, null, M_VIEW),
                ep(HttpMethod.GET, m + "/requests/{id}", null, null, M_VIEW),
                ep(HttpMethod.POST, m + "/requests", null, REQUEST_BODY, M_MANAGE),
                ep(HttpMethod.PUT, m + "/requests/{id}/status", "newStatus=IN_PROGRESS", null, M_MANAGE),
                ep(HttpMethod.GET, m + "/pm-schedules", null, null, M_VIEW),
                ep(HttpMethod.POST, m + "/pm-schedules", null, PM_BODY, M_MANAGE),
                ep(HttpMethod.POST, m + "/pm-schedules/{id}/generate-request", null, null, M_MANAGE),
                ep(HttpMethod.GET, m + "/requests/{id}/activities", null, null, M_VIEW),
                ep(HttpMethod.POST, m + "/requests/{id}/activities", null, ACTIVITY_BODY, M_MANAGE),
                ep(HttpMethod.GET, m + "/requests/{id}/spare-parts", null, null, M_VIEW, SP_VIEW),
                ep(HttpMethod.POST, m + "/requests/{id}/spare-parts", null, ISSUE_BODY, SP_ISSUE),
                ep(HttpMethod.GET, m + "/requests/{id}/cost", null, null, COST_VIEW),
                ep(HttpMethod.POST, m + "/requests/{id}/post-cost", null, null, COST_POST),
                ep(HttpMethod.GET, m + "/costs/unposted", null, null, COST_VIEW),
                ep(HttpMethod.POST, m + "/costs/repost-unposted", null, null, COST_POST),

                ep(HttpMethod.GET, oee + "/summary", null, null, OEE),
                ep(HttpMethod.GET, oee + "/machines", null, null, OEE),
                ep(HttpMethod.GET, oee + "/machines/{machineId}", null, null, OEE),
                ep(HttpMethod.GET, oee + "/trend", null, null, OEE),
                ep(HttpMethod.GET, oee + "/losses", null, null, OEE),
                ep(HttpMethod.GET, oee + "/downtime-reasons", null, null, OEE),

                ep(HttpMethod.GET, sch + "/gantt", null, null, S_VIEW),
                ep(HttpMethod.POST, sch + "/run", "dryRun=true", "{}", S_MANAGE),
                ep(HttpMethod.PUT, sch + "/operations/{operationId}/schedule", null, MOVE_BODY, S_MANAGE),
                ep(HttpMethod.DELETE, sch + "/operations/{operationId}/schedule", null, null, S_MANAGE));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean SparePartService sparePartService;
    @MockBean MaintenanceService maintenanceService;
    @MockBean MaintenanceCostService maintenanceCostService;
    @MockBean MaintenanceCostPoster maintenanceCostPoster;
    @MockBean OeeService oeeService;
    @MockBean SchedulingService schedulingService;

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

    @ParameterizedTest(name = "{0} - any authority it does not declare gets 403")
    @MethodSource("endpointStream")
    void otherAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - each declared authority is accepted")
    @MethodSource("endpointStream")
    void declaredAuthoritiesAreAccepted(Endpoint e) throws Exception {
        for (String declared : e.authorities()) {
            call(e, declared).andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    void matrixCoversEveryEndpointOfTheFourControllers() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(SparePartController.class, MaintenanceController.class, OeeController.class,
                SchedulingController.class)) {
            String base = c.getAnnotation(RequestMapping.class).value()[0];
            for (Method method : c.getDeclaredMethods()) {
                String key = key(method, base);
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

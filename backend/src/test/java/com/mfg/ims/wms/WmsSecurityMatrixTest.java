package com.mfg.ims.wms;

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
import com.mfg.ims.wms.service.BinService;
import com.mfg.ims.wms.service.CycleCountService;
import com.mfg.ims.wms.service.PickingService;
import com.mfg.ims.wms.service.PutAwayService;
import com.mfg.ims.wms.service.TransferOrderService;
import com.mfg.ims.wms.web.CycleCountController;
import com.mfg.ims.wms.web.PickingController;
import com.mfg.ims.wms.web.PutAwayRuleController;
import com.mfg.ims.wms.web.TransferOrderController;
import com.mfg.ims.wms.web.WmsBinController;
import com.mfg.ims.wms.web.WmsZoneController;

/**
 * 403 matrix for every WMS endpoint (R14-5.2). Web slice only, so it runs without Docker. Each endpoint must reject
 * anonymous callers (401), reject callers holding only authorities it does not accept (403) and accept each
 * authority it declares. A final test fails when a controller method is missing from the matrix.
 */
@WebMvcTest(controllers = { WmsZoneController.class, WmsBinController.class, PutAwayRuleController.class,
        TransferOrderController.class, PickingController.class, CycleCountController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class WmsSecurityMatrixTest {

    private static final String VIEW = "WMS_VIEW";
    private static final String BIN = "BIN_MANAGE";
    private static final String PUTAWAY = "PUTAWAY_MANAGE";
    private static final String PICKING = "PICKING_MANAGE";
    private static final String WAVE = "WAVE_MANAGE";
    private static final String TRANSFER = "TRANSFER_MANAGE";
    private static final String COUNT = "CYCLE_COUNT_MANAGE";
    private static final Set<String> EXECUTE = Set.of(PUTAWAY, PICKING, TRANSFER);
    private static final List<String> ALL_AUTHORITIES = List.of(VIEW, BIN, PUTAWAY, PICKING, WAVE, TRANSFER, COUNT,
            "SALES_VIEW", "SALES_CREATE", "INVENTORY_VIEW", "STOCK_ADJUST", "MRP_VIEW", "REPORT_VIEW");

    private static final String ZONE_BODY = "{\"warehouseId\":1,\"zoneCode\":\"A\",\"name\":\"Zone\",\"zoneType\":\"BULK\"}";
    private static final String BIN_BODY = "{\"zoneId\":1,\"binCode\":\"B1\"}";
    private static final String ASSIGN_BODY = "{\"materialId\":1}";
    private static final String RULE_BODY = "{\"warehouseId\":1,\"strategy\":\"FIXED_BIN\"}";
    private static final String TRANSFER_BODY = "{\"warehouseId\":1,\"lines\":[{\"itemType\":\"MATERIAL\",\"materialId\":1,"
            + "\"toBinId\":2,\"fromBinId\":3,\"quantity\":1}]}";
    private static final String CROSS_DOCK_BODY = "{\"goodsReceiptId\":1,\"targetType\":\"SALES_ORDER\",\"targetId\":1}";
    private static final String ASSIGN_TO_BODY = "{\"userId\":1}";
    private static final String TARGET_BODY = "{\"lineId\":1,\"toBinId\":2}";
    private static final String CONFIRM_BODY = "{\"lines\":[{\"lineId\":1,\"quantity\":1}]}";
    private static final String CANCEL_BODY = "{\"reason\":\"no longer needed\"}";
    private static final String WAVE_BODY = "{\"salesOrderIds\":[1]}";
    private static final String COUNT_BODY = "{\"warehouseId\":1,\"countType\":\"ZONE\",\"zoneId\":1}";
    private static final String COUNTS_BODY = "{\"lines\":[{\"lineId\":1,\"countedQty\":1}]}";

    record Endpoint(HttpMethod method, String template, String query, Set<String> authorities, String body) {
        String url() {
            return query == null ? template : template + "?" + query;
        }

        @Override
        public String toString() {
            return method + " " + url() + " " + authorities;
        }
    }

    private static Endpoint ep(HttpMethod m, String template, String query, String authority, String body) {
        return new Endpoint(m, template, query, Set.of(authority), body);
    }

    static List<Endpoint> endpoints() {
        String zones = "/api/wms/zones";
        String bins = "/api/wms/bins";
        String rules = "/api/wms/putaway-rules";
        String tos = "/api/wms/transfer-orders";
        String waves = "/api/wms/waves";
        String counts = "/api/wms/cycle-counts";
        return List.of(
                ep(HttpMethod.GET, zones, "warehouseId=1", VIEW, null),
                ep(HttpMethod.POST, zones, null, BIN, ZONE_BODY),
                ep(HttpMethod.PUT, zones + "/{id}", null, BIN, ZONE_BODY),

                ep(HttpMethod.GET, bins, "warehouseId=1", VIEW, null),
                ep(HttpMethod.GET, bins + "/{id}", null, VIEW, null),
                ep(HttpMethod.POST, bins, null, BIN, BIN_BODY),
                ep(HttpMethod.PUT, bins + "/{id}", null, BIN, BIN_BODY),
                ep(HttpMethod.GET, bins + "/{id}/stock", null, VIEW, null),
                ep(HttpMethod.GET, bins + "/{id}/assignments", null, VIEW, null),
                ep(HttpMethod.POST, bins + "/{id}/assignments", null, BIN, ASSIGN_BODY),
                ep(HttpMethod.DELETE, bins + "/{id}/assignments/{aid}", null, BIN, null),

                ep(HttpMethod.GET, rules, "warehouseId=1", VIEW, null),
                ep(HttpMethod.POST, rules, null, BIN, RULE_BODY),
                ep(HttpMethod.DELETE, rules + "/{id}", null, BIN, null),

                ep(HttpMethod.GET, tos, null, VIEW, null),
                ep(HttpMethod.GET, tos + "/{id}", null, VIEW, null),
                ep(HttpMethod.POST, tos, null, TRANSFER, TRANSFER_BODY),
                ep(HttpMethod.POST, tos + "/putaway/from-receipt/{id}", null, PUTAWAY, null),
                ep(HttpMethod.POST, tos + "/cross-dock", null, PUTAWAY, CROSS_DOCK_BODY),
                new Endpoint(HttpMethod.POST, tos + "/{id}/assign", null, EXECUTE, ASSIGN_TO_BODY),
                new Endpoint(HttpMethod.POST, tos + "/{id}/start", null, EXECUTE, null),
                new Endpoint(HttpMethod.POST, tos + "/{id}/set-target", null, EXECUTE, TARGET_BODY),
                new Endpoint(HttpMethod.POST, tos + "/{id}/confirm", null, EXECUTE, CONFIRM_BODY),
                new Endpoint(HttpMethod.POST, tos + "/{id}/cancel", null, EXECUTE, CANCEL_BODY),

                ep(HttpMethod.POST, "/api/wms/picking/sales-orders/{id}", null, PICKING, null),
                ep(HttpMethod.GET, waves, null, VIEW, null),
                ep(HttpMethod.GET, waves + "/{id}", null, VIEW, null),
                ep(HttpMethod.POST, waves, null, WAVE, WAVE_BODY),
                ep(HttpMethod.POST, waves + "/{id}/release", null, WAVE, null),
                ep(HttpMethod.POST, waves + "/{id}/cancel", null, WAVE, CANCEL_BODY),
                ep(HttpMethod.GET, waves + "/{id}/pick-list", null, VIEW, null),

                ep(HttpMethod.GET, counts, null, VIEW, null),
                ep(HttpMethod.GET, counts + "/{id}", null, VIEW, null),
                ep(HttpMethod.POST, counts, null, COUNT, COUNT_BODY),
                ep(HttpMethod.PUT, counts + "/{id}/lines", null, COUNT, COUNTS_BODY),
                ep(HttpMethod.POST, counts + "/{id}/review", null, COUNT, null),
                ep(HttpMethod.POST, counts + "/{id}/reopen", null, COUNT, null),
                ep(HttpMethod.POST, counts + "/{id}/post", null, COUNT, null),
                ep(HttpMethod.POST, counts + "/{id}/cancel", null, COUNT, CANCEL_BODY));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean BinService binService;
    @MockBean TransferOrderService transferOrderService;
    @MockBean PutAwayService putAwayService;
    @MockBean PickingService pickingService;
    @MockBean CycleCountService cycleCountService;

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
        var builder = request(e.method(), e.url(), 1L, 1L).with(csrf());
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
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - each declared authority is accepted")
    @MethodSource("endpointStream")
    void declaredAuthoritiesAreAccepted(Endpoint e) throws Exception {
        for (String authority : e.authorities()) {
            call(e, authority).andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    void matrixCoversEveryWmsEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(WmsZoneController.class, WmsBinController.class, PutAwayRuleController.class,
                TransferOrderController.class, PickingController.class, CycleCountController.class)) {
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

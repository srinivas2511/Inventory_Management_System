package com.springmfg.ims.notification;

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
import com.springmfg.ims.notification.service.NotificationAdminService;
import com.springmfg.ims.notification.service.NotificationInboxService;
import com.springmfg.ims.notification.web.NotificationAdminController;
import com.springmfg.ims.notification.web.NotificationController;

/**
 * 403 matrix for every notification endpoint. A user's own inbox and preferences need only a signed-in user (so any
 * authenticated caller is accepted, anonymous gets 401); templates and the delivery log need NOTIFICATION_MANAGE.
 */
@WebMvcTest(controllers = { NotificationController.class, NotificationAdminController.class })
@Import({ SecurityConfig.class, ProblemDetailSecurityHandlers.class, GlobalExceptionHandler.class,
        CorrelationIdFilter.class })
@TestPropertySource(properties = "ims.security.cors-origins=http://localhost:4200")
class NotificationSecurityMatrixTest {

    private static final String MANAGE = "NOTIFICATION_MANAGE";
    private static final List<String> ALL_AUTHORITIES = List.of(MANAGE, "DOCUMENT_MANAGE", "DOCUMENT_VIEW",
            "ALERT_VIEW", "ALERT_MANAGE", "USER_UPDATE", "HR_VIEW", "PLM_VIEW");

    private static final String PREFERENCE_BODY =
            "{\"templateCode\":\"LOW_STOCK\",\"channel\":\"IN_APP\",\"enabled\":false}";
    private static final String TEMPLATE_BODY =
            "{\"channel\":\"BOTH\",\"subjectTemplate\":\"Hello\",\"bodyTemplate\":\"Body {{x}}\"}";

    /** Empty authorities means "any signed-in user". */
    record Endpoint(HttpMethod method, String template, Set<String> authorities, String body) {
        boolean anyAuthenticated() {
            return authorities.isEmpty();
        }

        @Override
        public String toString() {
            return method + " " + template + " " + (anyAuthenticated() ? "[authenticated]" : authorities);
        }
    }

    private static Endpoint ep(HttpMethod m, String template, String body, String... authorities) {
        return new Endpoint(m, template, Set.of(authorities), body);
    }

    static List<Endpoint> endpoints() {
        return List.of(
                ep(HttpMethod.GET, "/api/notifications", null),
                ep(HttpMethod.GET, "/api/notifications/unread-count", null),
                ep(HttpMethod.POST, "/api/notifications/{id}/read", null),
                ep(HttpMethod.POST, "/api/notifications/read-all", null),
                ep(HttpMethod.GET, "/api/notification-preferences", null),
                ep(HttpMethod.PUT, "/api/notification-preferences", PREFERENCE_BODY),

                ep(HttpMethod.GET, "/api/notification-templates", null, MANAGE),
                ep(HttpMethod.PUT, "/api/notification-templates/{id}", TEMPLATE_BODY, MANAGE),
                ep(HttpMethod.GET, "/api/notification-log", null, MANAGE));
    }

    static Stream<Endpoint> endpointStream() {
        return endpoints().stream();
    }

    static Stream<Endpoint> adminEndpointStream() {
        return endpoints().stream().filter(e -> !e.anyAuthenticated());
    }

    static Stream<Endpoint> selfServiceEndpointStream() {
        return endpoints().stream().filter(Endpoint::anyAuthenticated);
    }

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean IdempotencyKeyRepository idempotencyKeyRepository;
    @MockBean NotificationInboxService inboxService;
    @MockBean NotificationAdminService adminService;

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

    @ParameterizedTest(name = "{0} - any other authority gets 403")
    @MethodSource("adminEndpointStream")
    void undeclaredAuthoritiesAreForbidden(Endpoint e) throws Exception {
        for (String other : ALL_AUTHORITIES) {
            if (e.authorities().contains(other)) continue;
            call(e, other).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "{0} - declared authority is accepted")
    @MethodSource("adminEndpointStream")
    void declaredAuthorityIsAccepted(Endpoint e) throws Exception {
        for (String authority : e.authorities()) {
            call(e, authority).andExpect(status().is2xxSuccessful());
        }
    }

    @ParameterizedTest(name = "{0} - any signed-in user is accepted")
    @MethodSource("selfServiceEndpointStream")
    void anySignedInUserIsAccepted(Endpoint e) throws Exception {
        call(e, "HR_VIEW").andExpect(status().is2xxSuccessful());
    }

    @Test
    void matrixCoversEveryNotificationEndpoint() {
        Set<String> declared = new HashSet<>();
        for (Class<?> c : List.of(NotificationController.class, NotificationAdminController.class)) {
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
        }
        if (verb == null) return null;
        String path = base + (paths.length == 0 ? "" : paths[0]);
        return verb + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}

package com.springmfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Calls every parameterless {@code GET /api/**} endpoint as a real ADMIN and asserts none answers 5xx. The
 * endpoints are discovered from the Spring MVC handler mappings, so a new list endpoint is covered the day it
 * is added. Optional filters are all absent, which is exactly the shape that exposed PostgreSQL's "could not
 * determine data type of parameter" failure on untyped null bindings.
 *
 * <p>A 4xx (a required parameter missing, a feature that needs data) is acceptable here; a 5xx never is.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndpointSmokeIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Smoke#Test-2026";

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private String adminToken;

    private String username() {
        return "smoke_admin_" + runId;
    }

    @BeforeAll
    void loginAsAdmin() throws Exception {
        var actor = new CachedUser(9002L, "smoke-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            userAdminService.create(new CreateUserRequest(username(), null, "Smoke admin",
                    username() + "@smoke.test", null, PASSWORD, Set.of("ADMIN")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username() + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        assertThat(login.getResponse().getStatus()).as("login").isEqualTo(200);
        adminToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    @AfterAll
    void removeTheUser() {
        String like = "smoke_admin_" + runId;
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username = ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username = ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ?)", like);
        jdbc.update("DELETE FROM users WHERE username = ?", like);
    }

    private Set<String> parameterlessGetPaths() {
        Set<String> paths = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/api/") && !pattern.contains("{") && !pattern.contains("*")) {
                    paths.add(pattern);
                }
            }
        }
        return paths;
    }

    @Test
    void everyParameterlessGetEndpointAnswersWithoutAServerError() throws Exception {
        Set<String> paths = parameterlessGetPaths();
        assertThat(paths).as("discovered GET endpoints").hasSizeGreaterThan(50);

        List<String> failures = new ArrayList<>();
        for (String path : paths) {
            try {
                int status = mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken)).andReturn()
                        .getResponse().getStatus();
                if (status >= 500) {
                    failures.add(status + " GET " + path);
                }
            } catch (Exception e) {
                // MockMvc rethrows unhandled exceptions; a real server answers these with a 500
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                failures.add("500 GET " + path + " -> " + root.getMessage());
            }
        }
        assertThat(failures).as("endpoints answering 5xx as ADMIN with no parameters").isEmpty();
    }

    /** A plausible value for an optional filter, chosen by the parameter's Java type. */
    private static String sampleValue(Class<?> type) {
        if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
            return "1";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true";
        }
        if (type == LocalDate.class) {
            return "2026-01-01";
        }
        if (type == Instant.class) {
            return "2026-01-01T00:00:00Z";
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0].toString();
        }
        return "x";
    }

    private String call(String method, String path, Map<String, String> params) {
        try {
            MockHttpServletRequestBuilder req = get(path).header("Authorization", "Bearer " + adminToken);
            params.forEach(req::param);
            int status = mockMvc.perform(req).andReturn().getResponse().getStatus();
            return status >= 500 ? status + " " + method + " " + path + " " + params : null;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return "500 " + method + " " + path + " " + params + " -> " + root.getMessage();
        }
    }

    @Test
    void everyOptionalFilterOnEveryListEndpointWorksOnItsOwn() {
        var discoverer = new DefaultParameterNameDiscoverer();
        List<String> failures = new ArrayList<>();
        int calls = 0;
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                continue;
            }
            Set<String> paths = new TreeSet<>();
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/api/") && !pattern.contains("{") && !pattern.contains("*")) {
                    paths.add(pattern);
                }
            }
            if (paths.isEmpty()) {
                continue;
            }
            for (MethodParameter parameter : handler.getMethodParameters()) {
                RequestParam rp = parameter.getParameterAnnotation(RequestParam.class);
                if (rp == null || rp.required() || !ValueConstants.DEFAULT_NONE.equals(rp.defaultValue())) {
                    continue;
                }
                parameter.initParameterNameDiscovery(discoverer);
                String name = rp.name().isEmpty() ? (rp.value().isEmpty() ? parameter.getParameterName() : rp.value())
                        : rp.name();
                if (name == null) {
                    continue;
                }
                for (String path : paths) {
                    calls++;
                    String failure = call("GET", path, Map.of(name, sampleValue(parameter.getParameterType())));
                    if (failure != null) {
                        failures.add(failure);
                    }
                }
            }
        }
        assertThat(calls).as("optional-filter calls made").isGreaterThan(30);
        assertThat(failures).as("filtered list calls answering 5xx as ADMIN").isEmpty();
    }

    @Test
    void everyReportRunsWithNoFiltersAndWithEachFilterAlone() throws Exception {
        String body = mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(body, "$[*].code");
        assertThat(codes).as("report codes").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (String code : codes) {
            String path = "/api/reports/" + code;
            for (Map<String, String> params : List.of(Map.<String, String>of(), Map.of("from", "2026-01-01"),
                    Map.of("to", "2026-12-31"), Map.of("status", "OPEN"), Map.of("entityId", "1"),
                    Map.of("from", "2026-01-01", "to", "2026-12-31", "status", "OPEN"))) {
                String failure = call("GET", path, params);
                if (failure != null) {
                    failures.add(failure);
                }
            }
        }
        assertThat(failures).as("report runs answering 5xx as ADMIN").isEmpty();
    }
}

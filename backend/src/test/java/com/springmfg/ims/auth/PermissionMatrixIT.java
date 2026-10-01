package com.springmfg.ims.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * Role x endpoint matrix over <b>every</b> mapped HTTP endpoint, generated from the seeded permissions: for each
 * of the 13 roles, the real method-security engine must grant or deny exactly what the endpoint's declared
 * permission and the seeded role map say. Phase 1 acceptance: "Role x endpoint matrix test generated from the
 * permission seed passes for all Phase 1 endpoints". New endpoints are covered automatically.
 */
class PermissionMatrixIT extends AbstractIntegrationTest {

    private static final Pattern CODE = Pattern.compile("'([A-Z_]+)'");

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;
    @Autowired
    JdbcTemplate jdbc;

    private Map<String, Set<String>> permissionsByRole() {
        Map<String, Set<String>> map = new HashMap<>();
        jdbc.query("""
                SELECT r.code AS role, p.code AS permission FROM roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.id LEFT JOIN permissions p ON p.id = rp.permission_id
                WHERE r.system_role""", rs -> {
            Set<String> perms = map.computeIfAbsent(rs.getString("role"), k -> new LinkedHashSet<>());
            if (rs.getString("permission") != null) {
                perms.add(rs.getString("permission"));
            }
        });
        return map;
    }

    /** Independent reading of the (convention-restricted) expression against a set of permission codes. */
    private static boolean expected(String expression, Set<String> permissions, boolean authenticated) {
        String e = expression.trim();
        if (e.equals("permitAll()")) {
            return true;
        }
        if (e.equals("isAuthenticated()")) {
            return authenticated;
        }
        Matcher codes = CODE.matcher(e);
        List<String> required = new ArrayList<>();
        while (codes.find()) {
            required.add(codes.group(1));
        }
        assertThat(e).as("expression form is covered by the conventions test").matches("has(Any)?Authority\\(.*\\)");
        return required.stream().anyMatch(permissions::contains);
    }

    @Test
    void everyEndpointGrantsAndDeniesEachRoleAccordingToTheSeed() {
        Map<String, Set<String>> roles = permissionsByRole();
        assertThat(roles).hasSize(13);
        PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();

        int endpoints = 0;
        int decisions = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapped : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = mapped.getValue();
            Method method = AopUtils.getMostSpecificMethod(handler.getMethod(), handler.getBeanType());
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            if (annotation == null) {
                continue; // framework endpoints (error page, actuator, springdoc); app endpoints are checked by ArchUnit
            }
            endpoints++;
            Object bean = handler.createWithResolvedBean().getBean(); // handler beans are registered by name
            for (Map.Entry<String, Set<String>> role : roles.entrySet()) {
                Authentication authentication = new JwtAuthentication(
                        new AuthenticatedUser(1, "u", List.of(role.getKey()), 1),
                        role.getValue().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
                boolean granted = manager.check(() -> authentication,
                        new SimpleMethodInvocation(bean, method)).isGranted();
                assertThat(granted)
                        .as("%s %s (%s) for role %s with %s", mapped.getKey().getMethodsCondition(),
                                mapped.getKey().getPathPatternsCondition(), annotation.value(), role.getKey(),
                                role.getValue().size() + " permissions")
                        .isEqualTo(expected(annotation.value(), role.getValue(), true));
                decisions++;
            }
        }
        assertThat(endpoints).as("application endpoints discovered").isGreaterThanOrEqualTo(7);
        assertThat(decisions).isEqualTo(endpoints * 13);
    }
}

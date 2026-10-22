package com.mfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * Every authority an endpoint demands must exist in the {@code permissions} table that Flyway builds, and must be
 * held by at least one role. Earlier releases shipped controllers guarded by codes nobody was ever granted
 * (MASTER_PRODUCT_VIEW, IAM_USER_MANAGE, ...), which hid behind tests that supplied the authority by hand.
 */
class PermissionCatalogueIT extends AbstractIntegrationTest {

    private static final Pattern HAS_AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([A-Za-z0-9_]+)'");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Autowired
    JdbcTemplate jdbc;

    /** authority code -> the endpoints that demand it */
    private Map<String, Set<String>> authoritiesDemandedByEndpoints() {
        Map<String, Set<String>> used = new TreeMap<>();
        handlerMapping.getHandlerMethods().forEach((info, hm) -> {
            PreAuthorize pre = preAuthorize(hm);
            if (pre == null) {
                return;
            }
            Matcher call = HAS_AUTHORITY.matcher(pre.value());
            while (call.find()) {
                Matcher code = QUOTED.matcher(call.group(1));
                while (code.find()) {
                    used.computeIfAbsent(code.group(1), k -> new TreeSet<>())
                            .add(info.toString() + " -> " + hm.getBeanType().getSimpleName());
                }
            }
        });
        return used;
    }

    private static PreAuthorize preAuthorize(HandlerMethod hm) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), PreAuthorize.class);
        return onMethod != null ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), PreAuthorize.class);
    }

    @Test
    @DisplayName("Every authority an endpoint requires is seeded in the permissions table")
    void everyRequiredAuthorityIsSeeded() {
        Map<String, Set<String>> used = authoritiesDemandedByEndpoints();
        Set<String> seeded = Set.copyOf(jdbc.queryForList("SELECT code FROM permissions", String.class));

        assertThat(used).as("the scan must actually find endpoint authorities").hasSizeGreaterThan(50);

        Map<String, Set<String>> unseeded = new TreeMap<>();
        used.forEach((code, endpoints) -> {
            if (!seeded.contains(code)) {
                unseeded.put(code, endpoints);
            }
        });
        assertThat(unseeded)
                .as("authorities demanded by endpoints but missing from the permissions table "
                        + "(no role can ever hold them)")
                .isEmpty();
    }

    @Test
    @DisplayName("Every authority an endpoint requires is granted to at least one role")
    void everyRequiredAuthorityIsGrantedToSomeRole() {
        Map<String, Set<String>> used = authoritiesDemandedByEndpoints();
        Set<String> granted = Set.copyOf(jdbc.queryForList(
                "SELECT DISTINCT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id",
                String.class));

        Map<String, Set<String>> orphaned = new TreeMap<>();
        used.forEach((code, endpoints) -> {
            if (!granted.contains(code)) {
                orphaned.put(code, endpoints);
            }
        });
        assertThat(orphaned)
                .as("authorities that exist but are granted to no role (their endpoints are unreachable)")
                .isEmpty();
    }
}

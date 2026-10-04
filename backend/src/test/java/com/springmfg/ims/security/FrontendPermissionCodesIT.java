package com.springmfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springmfg.ims.support.AbstractIntegrationTest;

/**
 * The Angular route guards, navigation entries and template checks gate screens on permission codes. A code that no
 * migration seeds (the settings screen once required the literal {@code ADMIN}) or that no role is granted hides the
 * screen from everyone, and no frontend test can notice because the guard simply answers "no". This reads the
 * frontend sources and holds every code they mention against the real {@code permissions} table.
 */
class FrontendPermissionCodesIT extends AbstractIntegrationTest {

    private static final Path FRONTEND_APP = Path.of("..", "frontend", "src", "app");

    private static final Pattern CODE = Pattern.compile("'([A-Z][A-Z0-9_]+)'");

    /** Every syntactic place the frontend names a permission: guards, checks, nav/route config, template directive. */
    private static final Pattern[] USAGES = {
            Pattern.compile("(?:permissionGuard|hasPermission|hasAnyPermission|hasAllPermissions)\\(\\s*(\\[[^\\]]*\\]|'[^']*')"),
            Pattern.compile("\\bpermission\\s*:\\s*('[^']*')"),
            Pattern.compile("\\bpermissions\\s*:\\s*(\\[[^\\]]*\\])"),
            Pattern.compile("appHasPermission\\s*\\]?\\s*=\\s*\"([^\"]*)\""),
            Pattern.compile("PERMISSION[A-Z_]*\\s*(?::[^=]+)?=\\s*\\{([^}]*)\\}"),
    };

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Every permission code the frontend checks is seeded and granted to at least one role")
    void frontendPermissionCodesAreSeededAndGranted() throws IOException {
        assertThat(Files.isDirectory(FRONTEND_APP))
                .as("frontend sources must be reachable from the backend module (looked in %s)",
                        FRONTEND_APP.toAbsolutePath().normalize())
                .isTrue();

        Map<String, Set<String>> usedIn = frontendPermissionCodes();
        assertThat(usedIn.size()).as("the scan should find the guards and nav entries, not nothing").isGreaterThan(30);

        Set<String> seeded = Set.copyOf(jdbc.queryForList("SELECT code FROM permissions", String.class));
        Map<String, Set<String>> notSeeded = new TreeMap<>();
        usedIn.forEach((code, files) -> {
            if (!seeded.contains(code)) {
                notSeeded.put(code, files);
            }
        });
        assertThat(notSeeded)
                .as("permission codes the frontend checks that no migration seeds (use the code the backend enforces)")
                .isEmpty();

        Set<String> granted = Set.copyOf(jdbc.queryForList(
                "SELECT DISTINCT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id",
                String.class));
        Map<String, Set<String>> notGranted = new TreeMap<>();
        usedIn.forEach((code, files) -> {
            if (!granted.contains(code)) {
                notGranted.put(code, files);
            }
        });
        assertThat(notGranted)
                .as("permission codes the frontend checks that no role holds (the screen is unreachable for everyone)")
                .isEmpty();
    }

    private static Map<String, Set<String>> frontendPermissionCodes() throws IOException {
        Map<String, Set<String>> codes = new TreeMap<>();
        try (Stream<Path> files = Files.walk(FRONTEND_APP)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString();
                if (!(name.endsWith(".ts") || name.endsWith(".html")) || name.endsWith(".spec.ts")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (Pattern usage : USAGES) {
                    Matcher m = usage.matcher(source);
                    while (m.find()) {
                        Matcher literal = CODE.matcher(m.group(1));
                        while (literal.find()) {
                            codes.computeIfAbsent(literal.group(1), k -> new TreeSet<>())
                                    .add(FRONTEND_APP.relativize(file).toString().replace('\\', '/'));
                        }
                    }
                }
            }
        }
        return codes;
    }
}

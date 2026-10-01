package com.springmfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.security.access.prepost.PreAuthorize;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import org.junit.jupiter.api.Test;

/**
 * {@code @PreAuthorize} conventions (DESIGN.md section 7.4). Access is expressed only as permission codes from
 * the seeded catalogue, so the role x endpoint matrix can be generated from the seed and a typo in a code
 * (which would silently lock everyone out, or worse) fails the build.
 */
class PreAuthorizeConventionTest {

    /** The only expression forms allowed; anything richer belongs in a service-level check (state, scope, SoD). */
    private static final Pattern ALLOWED = Pattern.compile(
            "permitAll\\(\\)|isAuthenticated\\(\\)|hasAuthority\\('([A-Z_]+)'\\)"
                    + "|hasAnyAuthority\\('([A-Z_]+)'(?:,\\s*'([A-Z_]+)')*\\)");
    private static final Pattern CODE = Pattern.compile("'([A-Z_]+)'");

    /** Every permission code inserted by a Flyway migration. */
    static Set<String> catalogue() throws IOException {
        Pattern row = Pattern.compile("^\\s*\\('([A-Z][A-Z_]+)',\\s*'[A-Z_]+',\\s*'", Pattern.MULTILINE);
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file);
                int start = sql.indexOf("INSERT INTO permissions");
                if (start >= 0) {
                    Matcher m = row.matcher(sql.substring(start));
                    while (m.find()) {
                        codes.add(m.group(1));
                    }
                }
            }
        }
        return codes;
    }

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.springmfg.ims");

    @Test
    void permissionCatalogueIsLoaded() throws IOException {
        org.assertj.core.api.Assertions.assertThat(catalogue()).hasSizeGreaterThanOrEqualTo(75).contains("PRODUCT_UPDATE");
    }

    @Test
    void preAuthorizeUsesOnlyTheAllowedFormsAndKnownPermissionCodes() throws IOException {
        Set<String> known = catalogue();
        ArchCondition<JavaMethod> condition = new ArchCondition<>("use an allowed expression with known permission codes") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String expression = method.getAnnotationOfType(PreAuthorize.class).value().trim();
                if (!ALLOWED.matcher(expression).matches()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " uses @PreAuthorize(\""
                            + expression + "\"); allowed: permitAll(), isAuthenticated(), hasAuthority('X'), "
                            + "hasAnyAuthority('X', 'Y')"));
                    return;
                }
                Matcher codes = CODE.matcher(expression);
                while (codes.find()) {
                    if (!known.contains(codes.group(1))) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName() + " refers to unknown "
                                + "permission " + codes.group(1) + " (not in the seeded catalogue)"));
                    }
                }
            }
        };
        methods().that().areAnnotatedWith(PreAuthorize.class).should(condition).allowEmptyShould(true).check(MAIN);
    }
}

package com.springmfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture guard rails (ARCHITECTURE.md sections 3, 4, 17). These rules are cheap now and protect every
 * later phase: a violation fails the build.
 */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Every HTTP handler must declare its access rule; no endpoint may ship unprotected (PLAN task 1.4). */
    @ArchTest
    static final ArchRule every_endpoint_declares_access =
            methods().that().areAnnotatedWith(GetMapping.class)
                    .or().areAnnotatedWith(PostMapping.class)
                    .or().areAnnotatedWith(PutMapping.class)
                    .or().areAnnotatedWith(PatchMapping.class)
                    .or().areAnnotatedWith(DeleteMapping.class)
                    .or().areAnnotatedWith(RequestMapping.class)
                    .should().beAnnotatedWith(PreAuthorize.class)
                    .because("authorization must be enforced by the backend on every endpoint (403 rule)");

    /** Controller -> Service -> Repository -> Database: controllers never touch repositories directly. */
    @ArchTest
    static final ArchRule controllers_do_not_use_repositories =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .allowEmptyShould(true)
                    .because("controllers call services; services own transactions and business rules");

    /** Entities never cross the API boundary: controllers must not depend on JPA entities. */
    @ArchTest
    static final ArchRule controllers_do_not_expose_entities =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .because("use DTOs rather than exposing JPA entities through REST");

    /** Services must not depend on controllers (no upward dependencies). */
    @ArchTest
    static final ArchRule services_do_not_depend_on_controllers =
            noClasses().that().haveSimpleNameEndingWith("Service")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
                    .allowEmptyShould(true); // no services exist yet in Phase 0

    /** Business code must not use field injection. */
    @ArchTest
    static final ArchRule no_field_injection =
            noClasses().should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
                    .because("use constructor injection");

    /** Everything lives under the application root package. */
    @ArchTest
    static final ArchRule common_does_not_depend_on_features =
            noClasses().that().resideInAPackage("..common..")
                    .should().dependOnClassesThat().resideInAnyPackage("..system..", "..inventory..", "..production..")
                    .because("shared kernel must not depend on feature modules");
}

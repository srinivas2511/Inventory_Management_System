package com.springmfg.ims.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** HRM architecture rules (R13): payroll posts only through JournalService and HR never touches stock. */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class HrmArchTest {

    /** CLAUDE.md rule 1: HR never touches stock. */
    @ArchTest
    static final ArchRule hrmDoesNotTouchStock =
            noClasses().that().resideInAPackage("..hrm..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory..", "..warehouse..")
                    .allowEmptyShould(true)
                    .because("only InventoryPostingService writes stock; HR is people and payroll data only");

    /** CLAUDE.md rule 15: GL writes go through JournalService only; HR never touches the journal tables. */
    @ArchTest
    static final ArchRule hrmWritesGlOnlyThroughJournalService =
            noClasses().that().resideInAPackage("..hrm..")
                    .should().dependOnClassesThat(simpleName("JournalHeader").or(simpleName("JournalLine"))
                            .or(simpleName("JournalHeaderRepository")).or(simpleName("JournalLineRepository")))
                    .allowEmptyShould(true)
                    .because("journal_headers/journal_lines are written by JournalService only");

    /** HR reaches production only through its events, never its services, entities or repositories. */
    @ArchTest
    static final ArchRule hrmListensToProductionEventsOnly =
            noClasses().that().resideInAPackage("..hrm..")
                    .should().dependOnClassesThat(describe("production classes other than events",
                            c -> c.getPackageName().contains(".production.")
                                    && !c.getPackageName().endsWith(".production.event")))
                    .allowEmptyShould(true)
                    .because("attendance capture is event-driven so production stays unaware of HR");

    /** Production and finance stay unaware of HR: payroll calls out, never in. */
    @ArchTest
    static final ArchRule nothingOutsideHrmDependsOnHrm =
            noClasses().that().resideOutsideOfPackage("..hrm..")
                    .should().dependOnClassesThat().resideInAPackage("..hrm..")
                    .allowEmptyShould(true)
                    .because("HR depends on finance and production events, not the reverse");
}

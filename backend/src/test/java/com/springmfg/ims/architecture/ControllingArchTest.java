package com.springmfg.ims.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Controlling-specific architecture rules (R10-5, CLAUDE.md rules 1, 13 and 15).
 */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class ControllingArchTest {

    /** Variance and costing never touch stock: the ledger belongs to InventoryPostingService. */
    @ArchTest
    static final ArchRule controllingDoesNotUseInventory =
            noClasses().that().resideInAPackage("..controlling..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory.service..", "..inventory.repository..", "..inventory.domain..")
                    .allowEmptyShould(true)
                    .because("controlling reads production data and posts GL journals only; it must not write stock");

    /** GL writes go through JournalService only (rule 15): no direct access to journal tables. */
    @ArchTest
    static final ArchRule controllingWritesGlOnlyThroughJournalService =
            noClasses().that().resideInAPackage("..controlling..")
                    .should().dependOnClassesThat(simpleName("JournalHeader").or(simpleName("JournalLine"))
                            .or(simpleName("JournalHeaderRepository")).or(simpleName("JournalLineRepository")))
                    .allowEmptyShould(true)
                    .because("journal_headers/journal_lines are written by JournalService only");

    /** Production stays unaware of controlling: costing reacts to ProductionOrderClosedEvent. */
    @ArchTest
    static final ArchRule productionDoesNotDependOnControlling =
            noClasses().that().resideInAPackage("..production..")
                    .should().dependOnClassesThat().resideInAPackage("..controlling..")
                    .allowEmptyShould(true)
                    .because("variance posting is event-driven; production must not call controlling");

    /** Finance stays unaware of controlling; dimensions travel as plain ids on journal lines. */
    @ArchTest
    static final ArchRule financeDoesNotDependOnControlling =
            noClasses().that().resideInAPackage("..finance..")
                    .should().dependOnClassesThat().resideInAPackage("..controlling..")
                    .allowEmptyShould(true)
                    .because("controlling builds on finance, never the reverse");
}
